"""mistri desktop interface.

Pure Tkinter. Controls are drawn as vector shapes (see widgets.py) and every
size is derived from the screen's real DPI, so the window stays sharp and
uncluttered at any Windows scaling level, in light or dark mode.
"""
from __future__ import annotations

import os
import queue
import subprocess
import threading
import time
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from tkinter import font as tkfont

from . import PUBLISHER, __version__, helptext
from .core import cleanup, malware, repair, resources, startup, updates
from .core.util import (IS_WINDOWS, LOG_DIR, QUARANTINE_DIR, NeedsAdmin, ensure_dirs, get_logger, human,
                        is_admin, load_state, relaunch_as_admin, save_state)
from .widgets import Button, CheckBox, Radio, Switch

EXT_DIR = Path(__file__).resolve().parents[1] / "extension"
ASSETS = Path(__file__).resolve().parent / "assets"

# Segoe Fluent Icons (Windows 11) / Segoe MDL2 Assets (Windows 10) glyphs, with plain fallbacks.
ICONS = {
    "home": ("", "⌂"), "clean": ("", "✧"), "security": ("", "⛨"),
    "repair": ("", "⚒"), "startup": ("", "⏻"), "services": ("", "⚙"),
    "processes": ("", "≡"), "info": ("", "ⓘ"), "chevron": ("", "›"),
}

PALETTE = {
    "light": dict(bg="#f7f8fa", side="#eef0f3", card="#ffffff", border="#e2e5e9", fg="#1b1b1b",
                  muted="#5c6168", subtle="#8b9097", accent="#005fb8", accent_hover="#1468bd",
                  accent_press="#2b76c2", accent_text="#ffffff", hover="#e3e7ec", sel="#dfe6ee",
                  btn="#ffffff", btn_hover="#f4f5f7", btn_press="#eceef1", btn_disabled="#f0f1f3",
                  ctl_border="#d5d9de", ctl_strong="#868b92", field="#ffffff", focus="#1b1b1b",
                  row_sel="#dcebf8", trough="#e6e9ed", ok="#0f7b0f", warn="#9d5d00", bad="#c42b1c",
                  log_bg="#ffffff", log_fg="#2b2f33", pill="#edf0f3"),
    "dark": dict(bg="#1c1c1c", side="#202020", card="#2a2a2a", border="#363636", fg="#f2f2f2",
                 muted="#a8a8a8", subtle="#7b7b7b", accent="#60cdff", accent_hover="#57bbe9",
                 accent_press="#4ea8d2", accent_text="#000000", hover="#2b2b2b", sel="#323232",
                 btn="#2f2f2f", btn_hover="#363636", btn_press="#2b2b2b", btn_disabled="#2a2a2a",
                 ctl_border="#454545", ctl_strong="#9b9b9b", field="#1f1f1f", focus="#ffffff",
                 row_sel="#1d3b50", trough="#3a3a3a", ok="#6ccb5f", warn="#fce100", bad="#ff99a4",
                 log_bg="#202020", log_fg="#d7d7d7", pill="#353535"),
}

NAV = [("home", "Home"), ("clean", "Clean"), ("security", "Security"), ("repair", "Repair"),
       ("startup", "Startup"), ("services", "Services"), ("processes", "Processes")]


def system_prefers_dark() -> bool:
    if os.environ.get("MISTRI_THEME") in ("dark", "light"):
        return os.environ["MISTRI_THEME"] == "dark"
    if not IS_WINDOWS:
        return False
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER,
                            r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize") as k:
            return winreg.QueryValueEx(k, "AppsUseLightTheme")[0] == 0
    except OSError:
        return False


def ago(ts: float | None) -> str:
    if not ts:
        return "never"
    d = time.time() - ts
    if d < 3600:
        return "just now" if d < 120 else f"{int(d // 60)} min ago"
    if d < 86400 * 2:
        return f"{int(d // 3600)} h ago"
    return f"{int(d // 86400)} days ago"


def autowrap(label: ttk.Label, slack: int = 0) -> ttk.Label:
    """Wrap a label's text to whatever width the layout gives it (pack it with fill='x')."""
    last = [0]

    def on_size(e):
        # ignore 1–3 px jitter, which can otherwise bounce between two layouts forever
        if abs(e.width - last[0]) > 3:
            last[0] = e.width
            label.configure(wraplength=max(50, e.width - slack))
    label.bind("<Configure>", on_size)
    return label


# ====================================================================== containers

class ScrollFrame(ttk.Frame):
    """Vertically scrolling page; put content in `.body`."""

    def __init__(self, master, app: "App"):
        super().__init__(master)
        self.canvas = tk.Canvas(self, highlightthickness=0, borderwidth=0, background=app.pal["bg"])
        self.vsb = ttk.Scrollbar(self, orient="vertical", command=self.canvas.yview)
        self.body = ttk.Frame(self.canvas)
        self._win = self.canvas.create_window(0, 0, window=self.body, anchor="nw")
        self.canvas.configure(yscrollcommand=self.vsb.set)
        self.canvas.pack(side="left", fill="both", expand=True)
        self.vsb.pack(side="right", fill="y")
        self.body.bind("<Configure>", lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all")))
        self.canvas.bind("<Configure>", lambda e: self.canvas.itemconfigure(self._win, width=e.width))
        self.bind("<Enter>", lambda e: self._wheel(True))
        self.bind("<Leave>", lambda e: self._wheel(False))
        app.themed.append(lambda p: self.canvas.configure(background=p["bg"]))

    def _wheel(self, on: bool) -> None:
        if on:
            self.canvas.bind_all("<MouseWheel>", self._on_wheel)
            self.canvas.bind_all("<Button-4>", lambda e: self.canvas.yview_scroll(-3, "units"))
            self.canvas.bind_all("<Button-5>", lambda e: self.canvas.yview_scroll(3, "units"))
        else:
            for seq in ("<MouseWheel>", "<Button-4>", "<Button-5>"):
                self.canvas.unbind_all(seq)

    def _on_wheel(self, e) -> None:
        if self.canvas.yview() != (0.0, 1.0):
            self.canvas.yview_scroll(int(-e.delta / 120) * 3, "units")


class Popover:
    """Explanation bubble: opens on click of an ⓘ, closes on any outside click or Esc."""
    current: "Popover | None" = None

    def __init__(self, app: "App", anchor: tk.Widget, title: str, text: str):
        Popover.close()
        p = app.pal
        self.win = tk.Toplevel(app)
        self.win.overrideredirect(True)
        self.win.attributes("-topmost", True)
        frame = tk.Frame(self.win, background=p["card"], highlightthickness=1,
                         highlightbackground=p["ctl_border"], highlightcolor=p["ctl_border"])
        frame.pack(fill="both", expand=True)
        pad = app.px(18)
        tk.Label(frame, text=title, font=app.f_strong, background=p["card"], foreground=p["fg"],
                 anchor="w", justify="left").pack(fill="x", padx=pad, pady=(pad, app.px(6)))
        tk.Label(frame, text=text, font=app.f_body, background=p["card"], foreground=p["muted"],
                 wraplength=app.px(380), anchor="w", justify="left").pack(fill="x", padx=pad, pady=(0, pad))
        self.win.update_idletasks()
        w, h = self.win.winfo_reqwidth(), self.win.winfo_reqheight()
        x = anchor.winfo_rootx() - app.px(10)
        y = anchor.winfo_rooty() + anchor.winfo_height() + app.px(6)
        sw, sh = app.winfo_screenwidth(), app.winfo_screenheight()
        x = max(app.px(8), min(x, sw - w - app.px(8)))
        if y + h > sh - app.px(48):
            y = anchor.winfo_rooty() - h - app.px(6)
        self.win.geometry(f"+{x}+{y}")
        self.win.bind("<Escape>", lambda e: Popover.close())
        self.anchor = anchor
        Popover.current = self

    @staticmethod
    def close() -> None:
        if Popover.current is not None:
            try:
                Popover.current.win.destroy()
            except tk.TclError:
                pass
            Popover.current = None


class InfoButton(ttk.Label):
    def __init__(self, master, app: "App", title: str, text: str, card: bool = True):
        super().__init__(master, text=app.icon("info"), style=("C." if card else "") + "Info.TLabel",
                         cursor="hand2", takefocus=True)
        self.app, self.title, self.text = app, title, text
        for seq in ("<Button-1>", "<Return>", "<space>"):
            self.bind(seq, self._toggle)

    def _toggle(self, _e=None):
        if Popover.current is not None and Popover.current.anchor is self:
            Popover.close()
        else:
            Popover(self.app, self, self.title, self.text)
        return "break"


# ====================================================================== app

class App(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        ensure_dirs()
        self.log = get_logger()
        self.state_data = load_state()
        self.admin = is_admin()
        self.q: queue.Queue = queue.Queue()
        self.busy = False
        self.themed: list = []
        self.scale = max(1.0, self.winfo_fpixels("1i") / 96.0)
        self._fonts()
        self.title("mistri")
        self._window_icon()
        w = min(self.px(1240), int(self.winfo_screenwidth() * .92))
        h = min(self.px(820), int(self.winfo_screenheight() * .88))
        self.geometry(f"{w}x{h}")
        self.minsize(min(self.px(980), w), min(self.px(620), h))
        self.mode = self.state_data.get("theme") or ("dark" if system_prefers_dark() else "light")
        self.pal = PALETTE[self.mode]
        self._styles()

        self.columnconfigure(1, weight=1)
        self.rowconfigure(0, weight=1)
        self._sidebar()
        self.main = ttk.Frame(self)
        self.main.grid(row=0, column=1, sticky="nsew")
        self.main.rowconfigure(0, weight=1)
        self.main.columnconfigure(0, weight=1)
        self.pages: dict[str, tk.Widget] = {}
        self.current: str | None = None
        self._activity()
        for key, _ in NAV:
            getattr(self, f"_page_{key}")()
        self.show("home")
        self.bind_all("<Button-1>", self._maybe_close_popover, add="+")
        self.bind("<Configure>", lambda e: Popover.close() if e.widget is self else None, add="+")
        self.after(80, self._poll)
        self.after(4000, self._home_tick)
        self.after(2500, self._auto_update_check)
        self.emit(f"mistri {__version__} ready. Nothing changes until you press a button. Logs: {LOG_DIR}")

    def _window_icon(self) -> None:
        try:
            if IS_WINDOWS and (ASSETS / "mistri.ico").exists():
                self.iconbitmap(default=str(ASSETS / "mistri.ico"))
            else:
                self._icon_img = tk.PhotoImage(file=str(ASSETS / "mistri-64.png"))
                self.iconphoto(True, self._icon_img)
        except tk.TclError:
            pass

    def _logo(self, size: int) -> tk.PhotoImage | None:
        """Closest pre-rendered logo at or above `size` px (Tk can only shrink by whole factors)."""
        avail = [32, 48, 64, 96, 128, 256]
        pick = next((a for a in avail if a >= size), 256)
        try:
            img = tk.PhotoImage(file=str(ASSETS / f"mistri-{pick}.png"))
        except tk.TclError:
            return None
        f = max(1, pick // size)
        return img.subsample(f) if f > 1 else img

    # ------------------------------------------------------------ scaling, fonts, theme
    def px(self, n: float) -> int:
        return int(round(n * self.scale))

    def _fonts(self) -> None:
        fams = set(tkfont.families(self))
        body = next((f for f in ("Segoe UI Variable Text", "Segoe UI", "Inter", "Helvetica") if f in fams),
                    "TkDefaultFont")
        semi = next((f for f in ("Segoe UI Variable Display Semib", "Segoe UI Semibold") if f in fams), None)
        mono = next((f for f in ("Cascadia Mono", "Consolas", "DejaVu Sans Mono") if f in fams), "TkFixedFont")
        icon = next((f for f in ("Segoe Fluent Icons", "Segoe MDL2 Assets") if f in fams), None)

        def mk(size, bold=False):
            if bold and semi:
                return tkfont.Font(self, family=semi, size=size)
            return tkfont.Font(self, family=body, size=size, weight="bold" if bold else "normal")
        self.f_body, self.f_small, self.f_strong = mk(10), mk(9), mk(10, True)
        self.f_sub, self.f_title, self.f_metric = mk(13, True), mk(21, True), mk(24, True)
        self.f_mono = tkfont.Font(self, family=mono, size=9)
        self.icon_font = tkfont.Font(self, family=icon, size=11) if icon else self.f_body
        self.icon_big = tkfont.Font(self, family=icon, size=16) if icon else self.f_sub
        self.has_icon_font = icon is not None
        for name in ("TkDefaultFont", "TkTextFont", "TkHeadingFont", "TkMenuFont"):
            try:
                tkfont.nametofont(name).configure(family=body, size=10)
            except tk.TclError:
                pass

    def icon(self, key: str) -> str:
        glyph, fallback = ICONS[key]
        return glyph if self.has_icon_font else fallback

    def _styles(self) -> None:
        p = self.pal
        s = ttk.Style(self)
        s.theme_use("clam")      # fully stylable and DPI-neutral
        px = self.px
        s.configure(".", background=p["bg"], foreground=p["fg"], font=self.f_body, bordercolor=p["border"],
                    lightcolor=p["field"], darkcolor=p["field"], troughcolor=p["trough"],
                    fieldbackground=p["field"], selectbackground=p["row_sel"], selectforeground=p["fg"],
                    insertcolor=p["fg"], focuscolor=p["accent"])
        s.configure("TFrame", background=p["bg"])
        s.configure("TLabel", background=p["bg"], foreground=p["fg"])
        s.configure("Title.TLabel", font=self.f_title)
        s.configure("Sub.TLabel", font=self.f_sub)
        s.configure("Strong.TLabel", font=self.f_strong)
        s.configure("Muted.TLabel", foreground=p["muted"])
        s.configure("Small.TLabel", foreground=p["muted"], font=self.f_small)
        s.configure("Info.TLabel", foreground=p["accent"], font=self.icon_font, padding=(px(5), px(1)))
        # on cards: every style is configured explicitly (ttk style names don't inherit bg sideways)
        s.configure("C.TFrame", background=p["card"])
        for name, fg, font in (("C.TLabel", p["fg"], self.f_body), ("C.Strong.TLabel", p["fg"], self.f_strong),
                               ("C.Sub.TLabel", p["fg"], self.f_sub), ("C.Muted.TLabel", p["muted"], self.f_body),
                               ("C.Small.TLabel", p["muted"], self.f_small),
                               ("C.Metric.TLabel", p["fg"], self.f_metric),
                               ("C.Icon.TLabel", p["accent"], self.icon_big),
                               ("C.Info.TLabel", p["accent"], self.icon_font),
                               ("C.Ok.TLabel", p["ok"], self.f_strong), ("C.Warn.TLabel", p["warn"], self.f_strong),
                               ("C.Bad.TLabel", p["bad"], self.f_strong),
                               ("C.Link.TLabel", p["accent"], self.f_strong)):
            s.configure(name, background=p["card"], foreground=fg, font=font)
        s.configure("C.Info.TLabel", padding=(px(5), px(1)))
        s.configure("C.Pill.TLabel", background=p["pill"], foreground=p["muted"], font=self.f_small,
                    padding=(px(8), px(2)))
        s.configure("C.TSeparator", background=p["border"])
        s.configure("TSeparator", background=p["border"])
        # sidebar
        s.configure("Side.TFrame", background=p["side"])
        s.configure("Side.TLabel", background=p["side"], foreground=p["fg"])
        s.configure("SideMuted.TLabel", background=p["side"], foreground=p["muted"], font=self.f_small)
        s.configure("Brand.TLabel", background=p["side"], foreground=p["fg"], font=self.f_sub)
        for st, bg in (("", p["side"]), ("Hover", p["hover"]), ("Sel", p["sel"])):
            s.configure(f"Nav{st}.TFrame", background=bg)
            s.configure(f"Nav{st}.TLabel", background=bg, foreground=p["fg"],
                        font=self.f_strong if st == "Sel" else self.f_body, padding=(0, px(10)))
            s.configure(f"NavIcon{st}.TLabel", background=bg, foreground=p["accent"] if st == "Sel" else p["muted"],
                        font=self.icon_font, padding=(px(14), px(10), px(12), px(10)))
            s.configure(f"NavBar{st}.TFrame", background=p["accent"] if st == "Sel" else bg)
        # inputs
        s.configure("TEntry", padding=(px(8), px(6)), bordercolor=p["ctl_border"], lightcolor=p["field"],
                    darkcolor=p["field"])
        s.map("TEntry", bordercolor=[("focus", p["accent"])], lightcolor=[("focus", p["accent"])])
        s.configure("TCombobox", padding=(px(8), px(5)), arrowsize=px(14), bordercolor=p["ctl_border"],
                    background=p["btn"], arrowcolor=p["muted"], lightcolor=p["field"], darkcolor=p["field"])
        s.map("TCombobox", fieldbackground=[("readonly", p["field"])], background=[("active", p["btn_hover"])],
              selectbackground=[("readonly", p["field"])], selectforeground=[("readonly", p["fg"])],
              bordercolor=[("focus", p["accent"])])
        self.option_add("*TCombobox*Listbox.background", p["field"])
        self.option_add("*TCombobox*Listbox.foreground", p["fg"])
        self.option_add("*TCombobox*Listbox.selectBackground", p["row_sel"])
        self.option_add("*TCombobox*Listbox.selectForeground", p["fg"])
        self.option_add("*TCombobox*Listbox.font", self.f_body)
        # slim scrollbars without arrow buttons
        for orient in ("Vertical", "Horizontal"):
            s.layout(f"{orient}.TScrollbar", [(f"{orient}.Scrollbar.trough", {"sticky": "nswe", "children": [
                (f"{orient}.Scrollbar.thumb", {"expand": "1", "sticky": "nswe"})]})])
        s.configure("TScrollbar", troughcolor=p["bg"], background=p["ctl_border"], bordercolor=p["bg"],
                    lightcolor=p["ctl_border"], darkcolor=p["ctl_border"], arrowsize=px(9), gripcount=0)
        s.map("TScrollbar", background=[("active", p["ctl_strong"])], lightcolor=[("active", p["ctl_strong"])],
              darkcolor=[("active", p["ctl_strong"])])
        s.configure("Horizontal.TProgressbar", thickness=px(6), troughcolor=p["trough"], background=p["accent"],
                    bordercolor=p["trough"], lightcolor=p["accent"], darkcolor=p["accent"])
        # lists
        line = self.f_body.metrics("linespace")
        s.configure("Treeview", background=p["card"], fieldbackground=p["card"], foreground=p["fg"],
                    rowheight=line + px(16), borderwidth=0, bordercolor=p["card"], lightcolor=p["card"],
                    darkcolor=p["card"], font=self.f_body)
        s.map("Treeview", background=[("selected", p["row_sel"])], foreground=[("selected", p["fg"])])
        s.configure("Treeview.Heading", background=p["card"], foreground=p["muted"], font=self.f_small,
                    relief="flat", padding=(px(10), px(8)), bordercolor=p["border"], lightcolor=p["card"],
                    darkcolor=p["card"])
        s.map("Treeview.Heading", background=[("active", p["btn_hover"])])
        s.layout("Treeview", [("Treeview.treearea", {"sticky": "nswe"})])
        self.configure(background=p["bg"])

    def toggle_theme(self) -> None:
        self.mode = "light" if self.mode == "dark" else "dark"
        self.pal = PALETTE[self.mode]
        self._styles()
        for cb in list(self.themed):
            try:
                cb(self.pal)
            except tk.TclError:
                pass
        self._nav_paint()
        self.state_data = load_state()
        self.state_data["theme"] = self.mode
        save_state(self.state_data)

    # ------------------------------------------------------------ building blocks
    def card(self, parent, pad: int = 20, **pack) -> ttk.Frame:
        p = self.pal
        outer = tk.Frame(parent, background=p["card"], highlightthickness=1, highlightbackground=p["border"],
                         highlightcolor=p["border"])
        self.themed.append(lambda q, w=outer: w.configure(background=q["card"], highlightbackground=q["border"],
                                                          highlightcolor=q["border"]))
        inner = ttk.Frame(outer, style="C.TFrame", padding=self.px(pad))
        inner.pack(fill="both", expand=True)
        if pack:
            outer.pack(**pack)
        inner.outer = outer  # type: ignore[attr-defined]
        return inner

    def button(self, parent, text, command, kind="normal", where=False, **kw) -> Button:
        return Button(parent, self, text, command, kind=kind, on_card=where, **kw)

    def card_grid(self, parent, cols: int) -> ttk.Frame:
        """Grid with equal-width card columns and fixed gutters (cards go in even columns)."""
        g = ttk.Frame(parent)
        for c in range(cols):
            g.columnconfigure(2 * c, weight=1, uniform="card")
            if c < cols - 1:
                g.columnconfigure(2 * c + 1, minsize=self.px(14))
        return g

    def divider(self, parent) -> None:
        line = tk.Frame(parent, height=1, background=self.pal["border"])
        line.pack(fill="x", pady=self.px(14))
        self.themed.append(lambda q, w=line: w.configure(background=q["border"]))

    def header(self, parent, title: str, subtitle: str, help_key: str | None = None) -> None:
        h = ttk.Frame(parent)
        h.pack(fill="x", pady=(0, self.px(22)))
        row = ttk.Frame(h)
        row.pack(fill="x")
        ttk.Label(row, text=title, style="Title.TLabel").pack(side="left")
        if help_key:
            InfoButton(row, self, *helptext.PAGES[help_key], card=False).pack(
                side="left", padx=(self.px(6), 0), pady=(self.px(4), 0))
        autowrap(ttk.Label(h, text=subtitle, style="Muted.TLabel", justify="left")).pack(
            fill="x", pady=(self.px(6), 0))

    def section_title(self, parent, text: str, info: tuple[str, str] | None = None) -> None:
        row = ttk.Frame(parent, style="C.TFrame")
        row.pack(fill="x", pady=(0, self.px(14)))
        ttk.Label(row, text=text, style="C.Sub.TLabel").pack(side="left")
        if info:
            InfoButton(row, self, *info).pack(side="left", padx=(self.px(2), 0))

    def item_text(self, parent, title: str, desc: str, info: tuple[str, str] | None = None,
                  pill: str | None = None) -> ttk.Frame:
        """Title (+ ⓘ, + pill) over a one-line description, for list rows on cards."""
        txt = ttk.Frame(parent, style="C.TFrame")
        line = ttk.Frame(txt, style="C.TFrame")
        line.pack(anchor="w")
        ttk.Label(line, text=title, style="C.Strong.TLabel").pack(side="left")
        if info:
            InfoButton(line, self, *info).pack(side="left")
        if pill:
            ttk.Label(line, text=pill, style="C.Pill.TLabel").pack(side="left", padx=(self.px(6), 0))
        autowrap(ttk.Label(txt, text=desc, style="C.Small.TLabel", justify="left")).pack(
            fill="x", pady=(self.px(3), 0))
        return txt

    def page_frame(self, key: str, scroll: bool = True):
        pad = (self.px(40), self.px(32), self.px(40), self.px(32))
        if scroll:
            sf = ScrollFrame(self.main, self)
            sf.grid(row=0, column=0, sticky="nsew")
            body = ttk.Frame(sf.body, padding=pad)
            body.pack(fill="both", expand=True)
            self.pages[key] = sf
            return body
        f = ttk.Frame(self.main, padding=(pad[0], pad[1], pad[2], self.px(20)))
        f.grid(row=0, column=0, sticky="nsew")
        self.pages[key] = f
        return f

    def tree(self, parent, cols: list[tuple[str, str, int, bool]]) -> ttk.Treeview:
        """A list inside a card. cols: (key, title, width at 100% scale, stretch)."""
        c = self.card(parent, pad=6, fill="both", expand=True)
        tv = ttk.Treeview(c, columns=[k for k, *_ in cols], show="headings", selectmode="extended")
        for key, title, width, stretch in cols:
            tv.heading(key, text=title.upper(), anchor="w")
            tv.column(key, width=self.px(width), minwidth=self.px(60), stretch=stretch, anchor="w")
        sb = ttk.Scrollbar(c, orient="vertical", command=tv.yview)
        tv.configure(yscrollcommand=sb.set)
        tv.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y", padx=(self.px(4), 0))
        return tv

    def empty_note(self, tv: ttk.Treeview, text: str) -> None:
        if not tv.get_children():
            tv.insert("", "end", iid="__empty__", values=(text,), tags=("empty",))
            tv.tag_configure("empty", foreground=self.pal["muted"])

    # ------------------------------------------------------------ sidebar
    def _sidebar(self) -> None:
        side = ttk.Frame(self, style="Side.TFrame", width=self.px(232))
        side.grid(row=0, column=0, sticky="ns")
        side.pack_propagate(False)
        brand = ttk.Frame(side, style="Side.TFrame", padding=(self.px(22), self.px(26), self.px(16), self.px(22)))
        brand.pack(fill="x")
        self._brand_img = self._logo(self.px(40))
        if self._brand_img is not None:
            ttk.Label(brand, image=self._brand_img, style="Side.TLabel").pack(side="left", padx=(0, self.px(12)))
        names = ttk.Frame(brand, style="Side.TFrame")
        names.pack(side="left", fill="x")
        ttk.Label(names, text="mistri", style="Brand.TLabel").pack(anchor="w")
        ttk.Label(names, text=f"by {PUBLISHER}", style="SideMuted.TLabel").pack(anchor="w")
        self.nav_rows: dict[str, tuple] = {}
        self.hovered = None
        navbox = ttk.Frame(side, style="Side.TFrame", padding=(self.px(10), 0))
        navbox.pack(fill="x")
        for key, label in NAV:
            row = ttk.Frame(navbox, style="Nav.TFrame")
            row.pack(fill="x", pady=self.px(2))
            bar = ttk.Frame(row, style="NavBar.TFrame", width=self.px(3))
            bar.pack(side="left", fill="y", pady=self.px(10))
            ic = ttk.Label(row, text=self.icon(key), style="NavIcon.TLabel")
            ic.pack(side="left")
            tx = ttk.Label(row, text=label, style="Nav.TLabel")
            tx.pack(side="left", fill="x", expand=True)
            self.nav_rows[key] = (row, bar, ic, tx)
            for w in (row, ic, tx, bar):
                w.bind("<Button-1>", lambda e, k=key: self.show(k))
                w.bind("<Enter>", lambda e, k=key: self._nav_hover(k))
                w.bind("<Leave>", lambda e: self._nav_hover(None))

        bottom = ttk.Frame(side, style="Side.TFrame", padding=(self.px(18), self.px(16)))
        bottom.pack(side="bottom", fill="x")
        if self.admin:
            ttk.Label(bottom, text="Running as administrator", style="SideMuted.TLabel").pack(anchor="w")
        else:
            autowrap(ttk.Label(bottom, text="Standard user: some tools are locked", style="SideMuted.TLabel")
                     ).pack(fill="x")
            self.button(bottom, "Restart as administrator", self._elevate, "accent", "side").pack(
                anchor="w", pady=(self.px(10), 0))
        links = ttk.Frame(bottom, style="Side.TFrame")
        links.pack(fill="x", pady=(self.px(12), 0))
        self.button(links, "Open logs", lambda: self._open(LOG_DIR), where="side", small=True).pack(side="left")
        self.button(links, "Light / dark", self.toggle_theme, where="side", small=True).pack(
            side="left", padx=(self.px(8), 0))

    def _nav_hover(self, key) -> None:
        self.hovered = key
        self._nav_paint()

    def _nav_paint(self) -> None:
        for key, (row, bar, ic, tx) in self.nav_rows.items():
            st = "Sel" if key == self.current else ("Hover" if key == self.hovered else "")
            row.configure(style=f"Nav{st}.TFrame")
            ic.configure(style=f"NavIcon{st}.TLabel")
            tx.configure(style=f"Nav{st}.TLabel")
            bar.configure(style=f"NavBar{st}.TFrame")

    def show(self, key: str) -> None:
        Popover.close()
        self.current = key
        self.pages[key].tkraise()
        self._nav_paint()
        self.title(f"mistri — {dict(NAV)[key]}")
        if key == "processes":
            self._load_procs()
        if key == "home":
            self._refresh_home()

    def _maybe_close_popover(self, e) -> None:
        pc = Popover.current
        if pc is None:
            return
        try:
            if str(e.widget).startswith(str(pc.win)) or e.widget is pc.anchor:
                return
        except Exception:
            pass
        Popover.close()

    # ------------------------------------------------------------ activity drawer
    def _activity(self) -> None:
        p = self.pal
        wrap = ttk.Frame(self.main)
        wrap.grid(row=1, column=0, sticky="ew")
        ttk.Separator(wrap).pack(fill="x")
        bar = ttk.Frame(wrap, padding=(self.px(40), self.px(10), self.px(28), self.px(10)))
        bar.pack(fill="x")
        ttk.Label(bar, text="Activity", style="Strong.TLabel").pack(side="left")
        self.act_btn = self.button(bar, "Show log", self.toggle_activity, small=True, min_width=86)
        self.act_btn.pack(side="right")
        self.act_prog = ttk.Progressbar(bar, mode="indeterminate", length=self.px(140))
        self.act_last = ttk.Label(bar, text="Ready", style="Muted.TLabel")
        self.act_last.pack(side="left", padx=(self.px(14), self.px(14)), fill="x", expand=True)
        self.log_frame = ttk.Frame(wrap, padding=(self.px(40), 0, self.px(28), self.px(16)))
        lc = self.card(self.log_frame, pad=0, fill="both", expand=True)
        self.console = tk.Text(lc, height=9, font=self.f_mono, wrap="word", relief="flat", borderwidth=0,
                               padx=self.px(14), pady=self.px(12), background=p["log_bg"], foreground=p["log_fg"],
                               insertbackground=p["log_fg"], highlightthickness=0, spacing1=self.px(2))
        sb = ttk.Scrollbar(lc, orient="vertical", command=self.console.yview)
        self.console.configure(yscrollcommand=sb.set, state="disabled")
        self.console.pack(side="left", fill="both", expand=True)
        sb.pack(side="right", fill="y")
        self.themed.append(lambda q: self.console.configure(background=q["log_bg"], foreground=q["log_fg"]))
        self.log_open = False

    def toggle_activity(self, force: bool | None = None) -> None:
        self.log_open = (not self.log_open) if force is None else force
        if self.log_open:
            self.log_frame.pack(fill="x")
            self.act_btn.configure_text("Hide log")
        else:
            self.log_frame.pack_forget()
            self.act_btn.configure_text("Show log")

    def _set_busy(self, busy: bool) -> None:
        self.busy = busy
        if busy:
            self.act_prog.pack(side="right", padx=(0, self.px(14)), before=self.act_last)
            self.act_prog.start(12)
            self.toggle_activity(True)
        else:
            self.act_prog.stop()
            self.act_prog.pack_forget()

    # ------------------------------------------------------------ plumbing
    def emit(self, msg: str) -> None:
        self.q.put(("line", msg))

    def _poll(self) -> None:
        try:
            while True:
                kind, payload = self.q.get_nowait()
                if kind == "line":
                    self.log.info(payload)
                    self.console.configure(state="normal")
                    self.console.insert("end", payload + "\n")
                    if int(self.console.index("end-1c").split(".")[0]) > 6000:
                        self.console.delete("1.0", "1000.0")
                    self.console.see("end")
                    self.console.configure(state="disabled")
                    if payload.strip():
                        short = payload.strip().splitlines()[-1].strip("— ")
                        self.act_last.configure(text=short if len(short) < 120 else short[:117] + "…")
                else:
                    payload()
        except queue.Empty:
            pass
        self.after(80, self._poll)

    def ui(self, fn) -> None:
        self.q.put(("call", fn))

    def bg(self, work, done=None) -> None:
        def t():
            try:
                res = work()
            except Exception as ex:
                self.emit(f"[error] {ex}")
                return
            if done:
                self.ui(lambda: done(res))
        threading.Thread(target=t, daemon=True).start()

    def task(self, label: str, fn, *args, done=None, record: str | None = None, **kw) -> None:
        if self.busy:
            messagebox.showinfo("mistri", "Another task is still running. Watch its progress under Activity.")
            return
        self._set_busy(True)
        self.emit(f"\n— {label} —")

        def t():
            res, ok = None, False
            try:
                res = fn(*args, emit=self.emit, **kw)
                ok = True
            except NeedsAdmin as ex:
                why = str(ex)
                self.emit(f"[admin needed] {why}")
                self.ui(lambda: self._offer_elevate(why))
            except Exception as ex:
                self.emit(f"[error] {type(ex).__name__}: {ex}")
            finally:
                def fin():
                    self._set_busy(False)
                    if ok and record:
                        st = load_state()
                        st.setdefault("last", {})[record] = time.time()
                        save_state(st)
                    if done:
                        done(res)
                self.ui(fin)
        threading.Thread(target=t, daemon=True).start()

    def _offer_elevate(self, why: str) -> None:
        if messagebox.askyesno("Administrator needed", f"{why}\n\nRestart mistri as administrator now?"):
            self._elevate()

    def _elevate(self) -> None:
        if relaunch_as_admin(["gui"]):
            self.destroy()

    @staticmethod
    def _open(path) -> None:
        Path(path).mkdir(parents=True, exist_ok=True)
        if IS_WINDOWS:
            os.startfile(str(path))  # type: ignore[attr-defined]

    # ================================================================ HOME
    def _page_home(self) -> None:
        b = self.page_frame("home")
        self.header(b, "Your PC at a glance",
                    "Live status, protection, and what each tool is for. Click any tool to open it.", "home")
        self.upd_wrap = ttk.Frame(b)
        self.upd_wrap.pack(fill="x")
        self.upd_rel = None
        grid = self.card_grid(b, 3)
        grid.pack(fill="x")
        self.metric = {}
        for i, (key, label) in enumerate((("cpu", "Processor"), ("ram", "Memory"), ("disk", "System drive"))):
            c = self.card(grid)
            c.outer.grid(row=0, column=2 * i, sticky="nsew")
            ttk.Label(c, text=label, style="C.Muted.TLabel").pack(anchor="w")
            val = ttk.Label(c, text="–", style="C.Metric.TLabel")
            val.pack(anchor="w", pady=(self.px(2), self.px(10)))
            bar = ttk.Progressbar(c, maximum=100)
            bar.pack(fill="x")
            note = ttk.Label(c, text="", style="C.Small.TLabel")
            note.pack(anchor="w", pady=(self.px(10), 0))
            self.metric[key] = (val, bar, note)
        self.status_cards = {}
        for i, (key, label) in enumerate((("protect", "Protection"), ("maint", "Maintenance"),
                                          ("uptime", "Uptime"))):
            c = self.card(grid)
            c.outer.grid(row=1, column=2 * i, sticky="nsew", pady=(self.px(14), 0))
            ttk.Label(c, text=label, style="C.Muted.TLabel").pack(anchor="w")
            head = ttk.Label(c, text="Checking…", style="C.Strong.TLabel")
            head.pack(anchor="w", pady=(self.px(8), self.px(4)))
            detail = autowrap(ttk.Label(c, text="", style="C.Small.TLabel", justify="left"))
            detail.pack(fill="x")
            self.status_cards[key] = (head, detail)

        ttk.Label(b, text="Tools", style="Sub.TLabel").pack(anchor="w", pady=(self.px(32), self.px(12)))
        tg = self.card_grid(b, 3)
        tg.pack(fill="x")
        tools = [
            ("clean", "Clean", "Free up disk space by removing temp files, browser caches and crash leftovers."),
            ("security", "Security", "Scan for malware with Microsoft Defender and the open-source ClamAV."),
            ("repair", "Repair", "Fix damaged Windows files, check your drives and update your apps."),
            ("startup", "Startup", "Stop programs launching at sign-in so Windows starts faster."),
            ("services", "Services", "Tame background programs, with undo for every change."),
            ("processes", "Processes", "See what's using memory and CPU right now, and rein it in."),
        ]
        for i, (key, title, desc) in enumerate(tools):
            r, col = divmod(i, 3)
            c = self.card(tg)
            c.outer.grid(row=r, column=2 * col, sticky="nsew", pady=(0, self.px(14)))
            top = ttk.Frame(c, style="C.TFrame")
            top.pack(fill="x")
            ic = ttk.Label(top, text=self.icon(key), style="C.Icon.TLabel")
            ic.pack(side="left")
            tl = ttk.Label(top, text=title, style="C.Sub.TLabel")
            tl.pack(side="left", padx=(self.px(10), 0))
            d = autowrap(ttk.Label(c, text=desc, style="C.Muted.TLabel", justify="left"))
            d.pack(fill="x", pady=(self.px(10), self.px(14)))
            link = ttk.Label(c, text=f"Open {title}  {self.icon('chevron')}", style="C.Link.TLabel")
            link.pack(anchor="w")
            for w in (c, c.outer, top, ic, tl, d, link):
                w.bind("<Button-1>", lambda e, k=key: self.show(k))
                w.configure(cursor="hand2")

        ttk.Label(b, text="About mistri", style="Sub.TLabel").pack(anchor="w", pady=(self.px(18), self.px(12)))
        ab = self.card(b, fill="x")
        row = ttk.Frame(ab, style="C.TFrame")
        row.pack(fill="x")
        self.upd_btn = self.button(row, "Check for updates", self._check_updates, where=True)
        self.upd_btn.pack(side="right", anchor="n")
        txt = ttk.Frame(row, style="C.TFrame")
        txt.pack(side="left", fill="x", expand=True, padx=(0, self.px(14)))
        line = ttk.Frame(txt, style="C.TFrame")
        line.pack(anchor="w")
        ttk.Label(line, text=f"mistri {__version__} by {PUBLISHER}", style="C.Strong.TLabel").pack(side="left")
        InfoButton(line, self, "Updates", helptext.UPDATES).pack(side="left")
        self.upd_status = autowrap(ttk.Label(txt, text=self._update_status_text(), style="C.Small.TLabel", justify="left"))
        self.upd_status.pack(fill="x", pady=(self.px(3), 0))
        self.divider(ab)
        row2 = ttk.Frame(ab, style="C.TFrame")
        row2.pack(fill="x")
        self.upd_auto = tk.BooleanVar(value=updates.auto_enabled())
        Switch(row2, self, self.upd_auto, command=lambda: updates.set_auto(self.upd_auto.get())).pack(side="right")
        self.item_text(row2, "Check for updates automatically", "Once a day when mistri opens. Nothing installs without asking you.").pack(side="left", fill="x", expand=True)

    # ------------------------------------------------------------ updates
    def _update_status_text(self) -> str:
        u = load_state().get("updates", {})
        return f"Last checked: {ago(u.get('last_check'))}. Updates come from GitHub and must be signed by {PUBLISHER}."

    def _auto_update_check(self) -> None:
        if updates.due():
            self.bg(lambda: updates.check(lambda m: None), self._on_update_result)

    def _check_updates(self) -> None:
        def done(rel):
            self.upd_status.configure(text=self._update_status_text())
            if rel is None:
                messagebox.showinfo("mistri", f"You have the latest version (mistri {__version__}).")
            else:
                self._on_update_result(rel)
        self.task("Check for updates", updates.check, done=done)

    def _on_update_result(self, rel) -> None:
        if hasattr(self, "upd_status"):
            self.upd_status.configure(text=self._update_status_text())
        if not rel:
            return
        self.upd_rel = rel
        for w in self.upd_wrap.winfo_children():
            w.destroy()
        c = self.card(self.upd_wrap, fill="x", pady=(0, self.px(16)))
        row = ttk.Frame(c, style="C.TFrame")
        row.pack(fill="x")
        self.button(row, "Later", lambda: self.upd_wrap.winfo_children()[0].destroy(), where=True, small=True).pack(side="right", anchor="n")
        self.button(row, "What's new", updates.open_releases_page, where=True, small=True).pack(side="right", anchor="n", padx=(0, self.px(8)))
        self.button(row, "Update now", self._install_update, "accent", True, small=True).pack(side="right", anchor="n", padx=(0, self.px(8)))
        self.item_text(row, f"mistri {rel.version} is available", f"You have {__version__}. The installer is checked for {PUBLISHER}'s signature before it runs.").pack(side="left", fill="x", expand=True)

    def _install_update(self) -> None:
        rel = self.upd_rel
        if rel is None:
            return

        def done(path):
            if path and updates.launch_installer(path, self.emit):
                self.after(800, self.destroy)
        self.task(f"Update to mistri {rel.version}", updates.download_and_verify, rel, done=done)

    def _home_tick(self) -> None:
        if self.current == "home":
            self._refresh_home(full=False)
        self.after(4000, self._home_tick)

    def _refresh_home(self, full: bool = True) -> None:
        def work():
            import psutil
            vm = psutil.virtual_memory()
            disk = psutil.disk_usage(os.environ.get("SystemDrive", "C:") + "\\" if IS_WINDOWS else "/")
            return dict(cpu=psutil.cpu_percent(interval=0.4), cores=psutil.cpu_count() or 1, vm=vm, disk=disk,
                        up=time.time() - psutil.boot_time())
        self.bg(work, self._show_metrics)
        if full:
            self.bg(malware.defender_info, self._show_protection)

    def _show_metrics(self, d) -> None:
        for key, pct, note in (("cpu", d["cpu"], f"{d['cores']} logical cores"),
                               ("ram", d["vm"].percent, f"{human(d['vm'].used)} of {human(d['vm'].total)} in use"),
                               ("disk", d["disk"].percent, f"{human(d['disk'].free)} free of {human(d['disk'].total)}")):
            val, bar, lbl = self.metric[key]
            val.configure(text=f"{pct:.0f}%")
            bar.configure(value=pct)
            lbl.configure(text=note)
        hours = d["up"] / 3600
        head, detail = self.status_cards["uptime"]
        head.configure(text=f"{hours / 24:.1f} days" if hours >= 48 else f"{hours:.0f} hours",
                       style="C.Warn.TLabel" if hours > 24 * 7 else "C.Strong.TLabel")
        detail.configure(text="A restart clears memory leaks and finishes pending updates. Worth doing about "
                              "once a week." if hours > 72 else "Recently restarted.")
        last = load_state().get("last", {})
        head, detail = self.status_cards["maint"]
        lc, ls = last.get("clean"), last.get("scan")
        stale = not lc or time.time() - lc > 14 * 86400
        head.configure(text="Due for a clean-up" if stale else "Up to date",
                       style="C.Warn.TLabel" if stale else "C.Ok.TLabel")
        detail.configure(text=f"Last clean-up: {ago(lc)}\nLast mistri scan: {ago(ls)}")

    def _show_protection(self, info) -> None:
        head, detail = self.status_cards["protect"]
        clam = "ClamAV installed." if malware.clamav_dir() else "ClamAV not installed."
        if not info:
            head.configure(text="Defender status unavailable", style="C.Warn.TLabel")
            detail.configure(text=f"Another antivirus may be active. {clam}")
            return
        rt = info.get("RealTimeProtectionEnabled")
        age = info.get("AntivirusSignatureAge")
        head.configure(text="Protected" if rt else "Real-time protection is off",
                       style="C.Ok.TLabel" if rt else "C.Bad.TLabel")
        sig = "signatures updated today" if age == 0 else (f"signatures {age} days old" if age is not None else "")
        qs = info.get("QuickScanAge")
        scan = f"last scan {qs} days ago" if qs not in (None, 4294967295, 65535) else "no scan yet"
        detail.configure(text=f"Defender: {sig}, {scan}. {clam}")

    # ================================================================ CLEAN
    def _page_clean(self) -> None:
        b = self.page_frame("clean")
        self.header(b, "Clean up junk files",
                    "Frees space by removing files Windows and your browsers can safely recreate. Analyze first "
                    "to see sizes; nothing is deleted until you confirm.", "clean")
        bar = ttk.Frame(b)
        bar.pack(fill="x", pady=(0, self.px(16)))
        self.button(bar, "Analyze", self._clean_analyze).pack(side="left")
        self.clean_btn = self.button(bar, "Clean selected", self._clean_run, "accent")
        self.clean_btn.pack(side="left", padx=(self.px(10), 0))
        self.clean_btn.state(["disabled"])
        self.clean_total = ttk.Label(bar, text="", style="Strong.TLabel")
        self.clean_total.pack(side="left", padx=(self.px(18), 0))
        self.button(bar, "Schedule weekly…", self._schedule_dialog).pack(side="right")

        c = self.card(b, fill="x")
        self.clean_vars: dict[str, tk.BooleanVar] = {}
        self.clean_sizes: dict[str, ttk.Label] = {}
        self.clean_results: dict[str, cleanup.ScanResult] = {}
        for i, t in enumerate(cleanup.TARGETS):
            if i:
                self.divider(c)
            row = ttk.Frame(c, style="C.TFrame")
            row.pack(fill="x")
            locked = t.admin and not self.admin
            v = tk.BooleanVar(value=t.default and not locked)
            v.trace_add("write", lambda *a: self.clean_btn.state(["disabled"]))
            self.clean_vars[t.key] = v
            cb = CheckBox(row, self, v)
            cb.pack(side="left", anchor="n", pady=(self.px(1), 0))
            if locked:
                cb.state(["disabled"])
            size = ttk.Label(row, text="", style="C.Muted.TLabel", anchor="e")
            size.pack(side="right", anchor="n")
            self.clean_sizes[t.key] = size
            pill = "Needs admin" if locked else ("Off by default" if not t.default else None)
            self.item_text(row, t.name, t.description, (t.name, helptext.CLEAN.get(t.key, t.description)),
                           pill).pack(side="left", fill="x", expand=True, padx=(self.px(14), self.px(14)))

    def _clean_keys(self) -> list[str]:
        return [k for k, v in self.clean_vars.items() if v.get()]

    def _clean_analyze(self) -> None:
        keys = self._clean_keys()
        if not keys:
            messagebox.showinfo("mistri", "Tick at least one item to analyze.")
            return
        for lbl in self.clean_sizes.values():
            lbl.configure(text="")

        def done(results):
            if not results:
                return
            self.clean_results = {r.target.key: r for r in results}
            for r in results:
                txt = r.skipped_reason.capitalize() if r.skipped_reason else \
                    (human(r.size) if r.files else "Nothing to remove")
                self.clean_sizes[r.target.key].configure(text=txt)
            total = sum(r.size for r in results if not r.skipped_reason)
            self.clean_total.configure(text=f"{human(total)} can be freed" if total else "Already clean")
            if total:
                self.clean_btn.state(["!disabled"])
        self.task("Analyze junk files", cleanup.scan, keys, done=done)

    def _clean_run(self) -> None:
        keys = self._clean_keys()
        res = [self.clean_results[k] for k in keys if k in self.clean_results]
        if len(res) != len(keys):
            messagebox.showinfo("mistri", "Your selection changed. Press Analyze again first.")
            return
        total = sum(r.size for r in res if not r.skipped_reason)
        if not total:
            return
        if not messagebox.askyesno("Clean up", f"Delete {human(total)} of junk files?\n\n"
                                               "Files in use are skipped automatically."):
            return

        def done(_):
            self.clean_results.clear()
            self.clean_btn.state(["disabled"])
            self.clean_total.configure(text="Done. Analyze again to re-check.")
            for lbl in self.clean_sizes.values():
                lbl.configure(text="")
        self.task("Clean junk files", cleanup.clean, res, done=done, record="clean")

    def _schedule_dialog(self) -> None:
        if not messagebox.askyesno(
                "Weekly clean-up",
                "Create a Windows scheduled task that runs the default clean-up every Sunday at 03:00?\n\n"
                "To change or remove it later, open Command Prompt in mistri's install folder and run:\n"
                "  mistri schedule weekly --day WED --time 20:00\n"
                "  mistri schedule remove"):
            return
        from .cli import schedule_weekly
        self.task("Schedule weekly clean-up", schedule_weekly, "SUN", "03:00", self.admin)

    # ================================================================ SECURITY
    def _page_security(self) -> None:
        b = self.page_frame("security")
        self.header(b, "Malware protection",
                    "On-demand scans with Windows' own antivirus, plus an open-source second opinion.", "security")
        H = helptext.SECURITY
        has_clam = malware.clamav_dir() is not None
        self.use_def = tk.BooleanVar(value=True)
        self.use_clam = tk.BooleanVar(value=has_clam)
        self.quarantine = tk.BooleanVar(value=False)
        self.scan_kind = tk.StringVar(value="quick")
        self.scan_path = tk.StringVar()

        sc = self.card(b, fill="x")
        self.section_title(sc, "Scan")
        for i, (k, name, desc) in enumerate((("quick", "Quick scan", "The places malware usually hides. A few minutes."),
                                             ("full", "Full scan", "Every file on the PC. Can take an hour or more."),
                                             ("custom", "Scan a folder", "A drive, USB stick or download folder."))):
            if i:
                self.divider(sc)
            row = ttk.Frame(sc, style="C.TFrame")
            row.pack(fill="x")
            Radio(row, self, self.scan_kind, k).pack(side="left", anchor="n", pady=(self.px(1), 0))
            if k == "custom":
                self.button(row, "Browse…", self._pick_folder, where=True).pack(side="right", anchor="n")
                ttk.Entry(row, textvariable=self.scan_path, width=30).pack(side="right", anchor="n",
                                                                           padx=(0, self.px(8)))
            self.item_text(row, name, desc, (name, H[k])).pack(side="left", fill="x", expand=True,
                                                               padx=(self.px(14), self.px(14)))
        acts = ttk.Frame(sc, style="C.TFrame")
        acts.pack(fill="x", pady=(self.px(20), 0))
        self.button(acts, "Start scan", self._scan, "accent", True).pack(side="left")
        self.button(acts, "Update definitions", self._update_defs, where=True).pack(side="left",
                                                                                   padx=(self.px(10), 0))
        InfoButton(acts, self, "Update definitions", H["update"]).pack(side="left", padx=(self.px(2), 0))

        eng = self.card(b, fill="x", pady=(self.px(16), 0))
        self.section_title(eng, "Engines and options")
        rows = [(self.use_def, "Microsoft Defender", "Built into Windows. Main engine.", "defender", True),
                (self.use_clam, "ClamAV", "Open source. Second opinion." if has_clam else
                 "Not installed yet. About 500 MB including signatures.", "clamav", has_clam),
                (self.quarantine, "Quarantine what ClamAV finds",
                 "Off: report only. On: move flagged files where they can't run.", "quarantine", True)]
        for i, (var, name, desc, key, ok) in enumerate(rows):
            if i:
                self.divider(eng)
            row = ttk.Frame(eng, style="C.TFrame")
            row.pack(fill="x")
            sw = Switch(row, self, var)
            sw.pack(side="right", anchor="n", pady=(self.px(2), 0))
            if not ok:
                sw.state(["disabled"])
            if key == "clamav" and not ok:
                self.clam_install_btn = self.button(row, "Install ClamAV", self._install_clamav, "accent", True,
                                                    small=True)
                self.clam_install_btn.pack(side="right", anchor="n", padx=(0, self.px(14)))
                self.clam_switch = sw
                self.clam_desc_row = row
            self.item_text(row, name, desc, (name, H[key])).pack(side="left", fill="x", expand=True,
                                                                 padx=(0, self.px(14)))

        more = self.card_grid(b, 2)
        more.pack(fill="x", pady=(self.px(16), 0))
        hc = self.card(more)
        hc.outer.grid(row=0, column=0, sticky="nsew")
        self.section_title(hc, "History & quarantine")
        autowrap(ttk.Label(hc, text="See what Defender caught, or review files ClamAV quarantined.",
                           style="C.Muted.TLabel", justify="left")).pack(fill="x")
        r = ttk.Frame(hc, style="C.TFrame")
        r.pack(anchor="w", pady=(self.px(16), 0))
        self.button(r, "Threat history", lambda: self.task("Defender threat history", malware.defender_threats),
                    where=True).pack(side="left")
        self.button(r, "Quarantine", lambda: self._open(QUARANTINE_DIR), where=True).pack(
            side="left", padx=(self.px(8), 0))
        ec = self.card(more)
        ec.outer.grid(row=0, column=2, sticky="nsew")
        self.section_title(ec, "Browser protection", ("mistri Shield", H["extension"]))
        autowrap(ttk.Label(ec, text="Blocks ads, trackers, scam sites and popups in Chrome, and rejects optional "
                                    "cookies.", style="C.Muted.TLabel", justify="left")).pack(fill="x")
        self.button(ec, "Set up the Chrome extension…", self._extension_help, where=True).pack(
            anchor="w", pady=(self.px(16), 0))

    def _install_clamav(self) -> None:
        if not messagebox.askyesno(
                "Install ClamAV",
                "Download ClamAV from Cisco-Talos on GitHub (about 200 MB), check its signature, install it, "
                "then download its malware signatures (about 300 MB)?\n\nThis runs in the background; follow it "
                "under Activity."):
            return

        def done(rc):
            if malware.clamav_dir() is not None:
                self.clam_switch.state(["!disabled"])
                self.use_clam.set(True)
                self.clam_install_btn.pack_forget()
        self.task("Install ClamAV", malware.install_clamav, done=done)

    def _pick_folder(self) -> None:
        d = filedialog.askdirectory()
        if d:
            self.scan_path.set(d)
            self.scan_kind.set("custom")

    def _scan(self) -> None:
        kind = self.scan_kind.get()
        path = self.scan_path.get().strip() or None
        if kind == "custom" and not path:
            messagebox.showinfo("mistri", "Choose a folder to scan first.")
            return
        use_def, use_clam, quar = self.use_def.get(), self.use_clam.get(), self.quarantine.get()
        if not (use_def or use_clam):
            messagebox.showinfo("mistri", "Switch on at least one engine under Engines and options.")
            return

        def work(emit):
            if use_def:
                malware.defender_scan(kind, path if kind == "custom" else None, emit)
            if use_clam:
                paths = [path] if kind == "custom" else ([str(Path.home())] if kind == "full" else None)
                malware.clamav_scan(paths, quar, emit)
        self.task({"quick": "Quick scan", "full": "Full scan", "custom": "Folder scan"}[kind], work, record="scan")

    def _update_defs(self) -> None:
        use_def, use_clam = self.use_def.get(), self.use_clam.get()

        def work(emit):
            if use_def:
                malware.defender_update(emit)
            if use_clam:
                malware.clamav_update(emit)
        self.task("Update definitions", work)

    def _extension_help(self) -> None:
        messagebox.showinfo(
            "mistri Shield for Chrome",
            "1. Chrome opens chrome://extensions\n"
            "2. Turn on Developer mode (top right)\n"
            "3. Click Load unpacked and choose the folder that opens next\n\n"
            f"Folder: {EXT_DIR}")
        self._open(EXT_DIR)
        if IS_WINDOWS:
            subprocess.Popen(["cmd", "/c", "start", "", "chrome", "chrome://extensions"], creationflags=0x08000000)

    # ================================================================ REPAIR
    def _page_repair(self) -> None:
        b = self.page_frame("repair")
        self.header(b, "Repair & health",
                    "Microsoft's own tools for fixing Windows, checking drives and keeping apps current.", "repair")
        R = helptext.REPAIR
        groups = [
            ("Fix Windows", [
                ("Full repair", "Restore point, then DISM and SFC. The standard fix.", "15–40 min",
                 repair.full_repair, "full", True, True),
                ("Quick health check", "Read-only: tells you whether repairs are needed.", "~5 min",
                 repair.dism_scan, "dism_scan", True, False),
                ("System File Checker", "Repairs Windows system files on its own.", "5–20 min",
                 repair.sfc, "sfc", True, False),
                ("Clean update leftovers", "Removes superseded Windows components.", "5–15 min",
                 repair.component_cleanup, "component_cleanup", True, False),
            ]),
            ("Safety net", [
                ("Create restore point", "A snapshot to roll back to if something goes wrong.", "~1 min",
                 repair.restore_point, "restore_point", True, False),
            ]),
            ("Drives", [
                ("Check disk", "Looks for file-system errors without a restart.", "~5 min",
                 repair.chkdsk_scan, "chkdsk", True, False),
                ("Drive health", "Wear, temperature and error counts your drives report.", "seconds",
                 repair.disk_health, "disk_health", False, False),
            ]),
            ("Apps", [
                ("Check for app updates", "Lists installed apps that have newer versions.", "~30 sec",
                 repair.updates_list, "updates_list", False, False),
                ("Install all app updates", "Updates everything winget can.", "varies",
                 None, "updates_apply", False, False),
            ]),
        ]
        for gi, (gname, rows) in enumerate(groups):
            c = self.card(b, fill="x", pady=(0 if gi == 0 else self.px(16), 0))
            self.section_title(c, gname)
            for i, (title, desc, dur, fn, key, admin, rec) in enumerate(rows):
                if i:
                    self.divider(c)
                row = ttk.Frame(c, style="C.TFrame")
                row.pack(fill="x")
                cmd = self._confirm_updates if fn is None else (lambda fn=fn, t=title: self.task(t, fn))
                btn = self.button(row, "Run", cmd, "accent" if rec else "normal", True, min_width=84)
                btn.pack(side="right")
                if admin and not self.admin:
                    btn.state(["disabled"])
                ttk.Label(row, text=dur + ("  ·  admin" if admin else ""), style="C.Small.TLabel").pack(
                    side="right", padx=(self.px(14), self.px(18)))
                self.item_text(row, title, desc, (title, R[key]), "Recommended" if rec else None).pack(
                    side="left", fill="x", expand=True)

    def _confirm_updates(self) -> None:
        if messagebox.askyesno("Install app updates", "Install every available update now?\n"
                                                      "Apps being updated may close."):
            self.task("Install app updates", repair.updates_apply)

    # ================================================================ STARTUP
    def _page_startup(self) -> None:
        b = self.page_frame("startup", scroll=False)
        self.header(b, "Startup",
                    "Choose what launches when you sign in. Turning something off doesn't uninstall it, and you can "
                    "turn it back on any time.", "startup")
        bar = ttk.Frame(b)
        bar.pack(fill="x", pady=(0, self.px(14)))
        self.st_view = tk.StringVar(value="apps")
        self.seg_btns = {}
        for k, label in (("apps", "Startup apps"), ("tasks", "Scheduled tasks")):
            btn = self.button(bar, label, lambda k=k: self._startup_view(k))
            btn.pack(side="left", padx=(0, self.px(8)))
            self.seg_btns[k] = btn
        InfoButton(bar, self, *helptext.STARTUP_TASKS, card=False).pack(side="left")
        self.button(bar, "Refresh", self._startup_refresh).pack(side="right")
        self.button(bar, "Turn on", lambda: self._startup_toggle(True)).pack(side="right", padx=(0, self.px(8)))
        self.button(bar, "Turn off", lambda: self._startup_toggle(False), "accent").pack(
            side="right", padx=(0, self.px(8)))

        area = ttk.Frame(b)
        area.pack(fill="both", expand=True)
        self.st_apps = ttk.Frame(area)
        self.st_tv = self.tree(self.st_apps, [("name", "Name", 230, False), ("state", "Status", 80, False),
                                              ("where", "Added by", 190, False), ("cmd", "Command", 300, True)])
        self.st_tasks = ttk.Frame(area)
        self.tk_tv = self.tree(self.st_tasks, [("name", "Task", 320, False), ("state", "Status", 90, False),
                                               ("action", "Runs", 380, True)])
        self.st_detail = autowrap(ttk.Label(b, text="Select an item to see its full command.",
                                            style="Muted.TLabel", justify="left"))
        self.st_detail.pack(fill="x", pady=(self.px(12), 0))
        self.st_tv.bind("<<TreeviewSelect>>", lambda e: self._st_detail(self.st_tv, 3))
        self.tk_tv.bind("<<TreeviewSelect>>", lambda e: self._st_detail(self.tk_tv, 2))
        self.startup_items: dict[str, startup.StartupItem] = {}
        self.tasks_data: dict[str, dict] = {}
        self._startup_view("apps")
        self._load_startup()
        self._load_tasks()

    def _startup_view(self, k: str) -> None:
        self.st_view.set(k)
        for key, btn in self.seg_btns.items():
            btn.set_kind("accent" if key == k else "normal")
        (self.st_tasks if k == "apps" else self.st_apps).pack_forget()
        (self.st_apps if k == "apps" else self.st_tasks).pack(fill="both", expand=True)

    def _st_detail(self, tv, col) -> None:
        sel = [s for s in tv.selection() if s != "__empty__"]
        if sel:
            vals = tv.item(sel[0], "values")
            self.st_detail.configure(text=f"{vals[0]}  →  {vals[col]}")

    def _startup_refresh(self) -> None:
        self._load_startup() if self.st_view.get() == "apps" else self._load_tasks()

    def _startup_toggle(self, on: bool) -> None:
        verb = "Turn on" if on else "Turn off"
        if self.st_view.get() == "apps":
            items = [self.startup_items[i] for i in self.st_tv.selection() if i in self.startup_items]
            if not items:
                return

            def work(emit):
                for it in items:
                    startup.set_startup(it, on, emit)
            self.task(f"{verb} startup apps", work, done=lambda _: self._load_startup())
        else:
            tasks = [self.tasks_data[i] for i in self.tk_tv.selection() if i in self.tasks_data]
            if not tasks:
                return

            def work(emit):
                for t in tasks:
                    startup.set_task(t["Path"], t["Name"], on, emit)
            self.task(f"{verb} scheduled tasks", work, done=lambda _: self._load_tasks())

    def _load_startup(self) -> None:
        def done(items):
            self.st_tv.delete(*self.st_tv.get_children())
            self.startup_items = {}
            for it in items:
                self.startup_items[it.id] = it
                self.st_tv.insert("", "end", iid=it.id, values=(it.name, "On" if it.enabled else "Off",
                                                                it.location, it.command))
            self.empty_note(self.st_tv, "No startup apps found.")
        self.bg(startup.list_startup, done)

    def _load_tasks(self) -> None:
        def done(tasks):
            self.tk_tv.delete(*self.tk_tv.get_children())
            self.tasks_data = {}
            for i, t in enumerate(tasks):
                iid = f"t{i}"
                self.tasks_data[iid] = t
                self.tk_tv.insert("", "end", iid=iid, values=(f"{t.get('Path', '')}{t.get('Name', '')}",
                                                              t.get("State", ""), t.get("Action", "")))
            self.empty_note(self.tk_tv, "No third-party scheduled tasks found.")
        self.bg(startup.list_tasks, done)

    # ================================================================ SERVICES
    def _page_services(self) -> None:
        b = self.page_frame("services", scroll=False)
        self.header(b, "Services",
                    "Programs that run in the background. 'Manual' is the safe way to slim things down: the "
                    "service still starts when something needs it.", "services")
        bar = ttk.Frame(b)
        bar.pack(fill="x", pady=(0, self.px(14)))
        self.svc_filter = tk.StringVar()
        e = ttk.Entry(bar, textvariable=self.svc_filter, width=30)
        e.pack(side="left")
        e.bind("<KeyRelease>", lambda _: self._svc_filter_soon())
        ttk.Label(bar, text="Search", style="Muted.TLabel").pack(side="left", padx=(self.px(8), 0))
        self.svc_hide_ms = tk.BooleanVar(value=True)
        Switch(bar, self, self.svc_hide_ms, on_card=False, command=self._load_services).pack(
            side="left", padx=(self.px(28), self.px(8)))
        ttk.Label(bar, text="Hide Microsoft services").pack(side="left")
        self.button(bar, "Refresh", self._load_services).pack(side="right")
        self.svc_tv = self.tree(b, [("display", "Name", 300, True), ("name", "Service", 200, False),
                                    ("status", "Status", 90, False), ("start", "Start type", 100, False),
                                    ("changed", "Original", 95, False)])
        foot = ttk.Frame(b)
        foot.pack(fill="x", pady=(self.px(14), 0))
        acts = ttk.Frame(foot)
        acts.pack(side="right", anchor="n")
        ttk.Label(acts, text="Start type").pack(side="left", padx=(0, self.px(8)))
        self.svc_start = tk.StringVar(value="manual")
        ttk.Combobox(acts, textvariable=self.svc_start, values=["automatic", "delayed", "manual", "disabled"],
                     width=11, state="readonly").pack(side="left")
        self.button(acts, "Apply", self._svc_apply, "accent").pack(side="left", padx=(self.px(10), 0))
        self.button(acts, "Undo my change", self._svc_undo).pack(side="left", padx=(self.px(8), 0))
        self.svc_detail = autowrap(ttk.Label(foot, text="Select a service to see what it does.",
                                             style="Muted.TLabel", justify="left"))
        self.svc_detail.pack(side="left", fill="x", expand=True, anchor="n", padx=(0, self.px(24)))
        self.svc_tv.bind("<<TreeviewSelect>>", lambda e: self._svc_show())
        self.services: dict[str, resources.Service] = {}
        self._svc_after = None
        self._load_services()

    def _svc_filter_soon(self) -> None:
        if self._svc_after:
            self.after_cancel(self._svc_after)
        self._svc_after = self.after(300, self._load_services)

    def _load_services(self) -> None:
        flt, hide = self.svc_filter.get(), self.svc_hide_ms.get()

        def work():
            return resources.list_services(flt, hide), resources.changed_services()

        def done(res):
            svcs, changed = res
            self.svc_tv.delete(*self.svc_tv.get_children())
            self.services = {s.name: s for s in svcs}
            for s in svcs:
                disp = s.display + ("  · protected" if s.protected else "")
                self.svc_tv.insert("", "end", iid=s.name, values=(disp, s.name, s.status.capitalize(),
                                                                  s.start.capitalize(),
                                                                  changed.get(s.name, "").capitalize()))
            self.empty_note(self.svc_tv, "No services match.")
        self.bg(work, done)

    def _svc_show(self) -> None:
        sel = [s for s in self.svc_tv.selection() if s in self.services]
        if not sel:
            return
        s = self.services[sel[0]]
        desc = s.description or "This service doesn't describe itself."
        lock = "  Protected: Windows needs it." if s.protected else ""
        self.svc_detail.configure(text=f"{s.display}: {desc}{lock}")

    def _svc_apply(self) -> None:
        sel = [s for s in self.svc_tv.selection() if s in self.services]
        if not sel:
            return
        mode = self.svc_start.get()
        if mode == "disabled" and not messagebox.askyesno(
                "Disable service", "Disabling can stop the app that owns this service from working. "
                                   "'Manual' is usually enough.\n\nDisable anyway?"):
            return

        def work(emit):
            for name in sel:
                resources.set_service_start(name, mode, emit)
        self.task("Change service start type", work, done=lambda _: self._load_services())

    def _svc_undo(self) -> None:
        sel = [s for s in self.svc_tv.selection() if s in self.services]
        if not sel:
            return

        def work(emit):
            for name in sel:
                resources.undo_service(name, emit)
        self.task("Undo service change", work, done=lambda _: self._load_services())

    # ================================================================ PROCESSES
    def _page_processes(self) -> None:
        b = self.page_frame("processes", scroll=False)
        self.header(b, "Processes", "What's using your memory and processor right now.", "processes")
        bar = ttk.Frame(b)
        bar.pack(fill="x", pady=(0, self.px(14)))
        self.proc_sort = tk.StringVar(value="mem")
        self.sort_btns = {}
        for k, label in (("mem", "By memory"), ("cpu", "By CPU")):
            btn = self.button(bar, label, lambda k=k: self._proc_sort(k))
            btn.pack(side="left", padx=(0, self.px(8)))
            self.sort_btns[k] = btn
        self.button(bar, "Refresh", self._load_procs).pack(side="right")
        self.proc_summary = ttk.Label(bar, text="", style="Muted.TLabel")
        self.proc_summary.pack(side="right", padx=(0, self.px(18)))
        self.proc_tv = self.tree(b, [("name", "Name", 230, False), ("cpu", "CPU", 80, False),
                                     ("mem", "Memory", 100, False), ("pid", "PID", 80, False),
                                     ("exe", "Location", 300, True)])
        foot = ttk.Frame(b)
        foot.pack(fill="x", pady=(self.px(14), 0))
        self.button(foot, "End process", self._proc_kill).pack(side="left")
        ttk.Label(foot, text="Priority").pack(side="left", padx=(self.px(28), self.px(8)))
        self.proc_prio = tk.StringVar(value="below_normal")
        ttk.Combobox(foot, textvariable=self.proc_prio, values=resources.PRIORITIES, width=14,
                     state="readonly").pack(side="left")
        self.button(foot, "Set", self._proc_set_prio).pack(side="left", padx=(self.px(10), 0))
        self._proc_sort("mem", load=False)

    def _proc_sort(self, k: str, load: bool = True) -> None:
        self.proc_sort.set(k)
        for key, btn in self.sort_btns.items():
            btn.set_kind("accent" if key == k else "normal")
        if load:
            self._load_procs()

    def _load_procs(self) -> None:
        sort = self.proc_sort.get()

        def work():
            return resources.top_processes(80, sort), resources.system_summary()

        def done(res):
            procs, summary = res
            self.proc_summary.configure(text=summary.split("  |  Up")[0])
            self.proc_tv.delete(*self.proc_tv.get_children())
            for p in procs:
                self.proc_tv.insert("", "end", iid=str(p.pid), values=(p.name, f"{p.cpu:.1f}%", human(p.mem),
                                                                       p.pid, p.exe))
        self.bg(work, done)

    def _proc_kill(self) -> None:
        sel = self.proc_tv.selection()
        if not sel:
            return
        names = ", ".join(self.proc_tv.item(i, "values")[0] for i in sel)
        if not messagebox.askyesno("End process", f"End {names}?\nAnything unsaved in it will be lost."):
            return

        def work(emit):
            for pid in sel:
                resources.kill(int(pid), emit)
        self.task("End process", work, done=lambda _: self._load_procs())

    def _proc_set_prio(self) -> None:
        sel = self.proc_tv.selection()
        level = self.proc_prio.get()
        if not sel:
            return

        def work(emit):
            for pid in sel:
                resources.set_priority(int(pid), level, emit)
        self.task("Set priority", work)


def main() -> None:
    if IS_WINDOWS:
        import ctypes
        try:  # own taskbar button and icon, instead of grouping under "Python"
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("GITAV.mistri")
        except Exception:
            pass
        try:  # sharp text on high-DPI screens; every size above scales from the real DPI
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
        except Exception:
            pass
    App().mainloop()


if __name__ == "__main__":
    main()
