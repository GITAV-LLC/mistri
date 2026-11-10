"""Small vector-drawn controls (button, checkbox, switch, radio).

They're drawn on a Canvas at the screen's real DPI, so they stay crisp and
correctly sized at any Windows scaling level, and they follow the app's
light/dark palette.
"""
from __future__ import annotations

import tkinter as tk


def _surface(on_card) -> str:
    return "side" if on_card == "side" else ("card" if on_card else "bg")


def rounded(c: tk.Canvas, x1, y1, x2, y2, r, **kw):
    r = min(r, (x2 - x1) / 2, (y2 - y1) / 2)
    pts = [x1 + r, y1, x2 - r, y1, x2, y1, x2, y1 + r, x2, y2 - r, x2, y2, x2 - r, y2, x1 + r, y2,
           x1, y2, x1, y2 - r, x1, y1 + r, x1, y1]
    return c.create_polygon(pts, smooth=True, splinesteps=24, **kw)


class _Control(tk.Canvas):
    def __init__(self, master, app, on_card: bool, w: int, h: int, takefocus=True):
        super().__init__(master, width=w, height=h, highlightthickness=0, borderwidth=0,
                         background=app.pal[_surface(on_card)], takefocus=takefocus, cursor="hand2")
        self.app, self.on_card = app, on_card
        self.hover = self.pressed = self.disabled = self.focused = False
        self.bind("<Enter>", lambda e: self._set(hover=True))
        self.bind("<Leave>", lambda e: self._set(hover=False, pressed=False))
        self.bind("<ButtonPress-1>", lambda e: self._set(pressed=True))
        self.bind("<ButtonRelease-1>", self._release)
        self.bind("<FocusIn>", lambda e: self._set(focused=True))
        self.bind("<FocusOut>", lambda e: self._set(focused=False))
        self.bind("<space>", lambda e: self._fire())
        self.bind("<Return>", lambda e: self._fire())
        app.themed.append(self._retheme)
        self.after_idle(self.redraw)

    # ttk-compatible state API
    def state(self, specs):
        for s in specs:
            if s == "disabled":
                self.disabled = True
            elif s == "!disabled":
                self.disabled = False
        self.configure(cursor="arrow" if self.disabled else "hand2", takefocus=not self.disabled)
        self.redraw()

    def _set(self, **kw):
        for k, v in kw.items():
            setattr(self, k, v)
        self.redraw()

    def _release(self, e):
        inside = 0 <= e.x <= self.winfo_width() and 0 <= e.y <= self.winfo_height()
        was = self.pressed
        self._set(pressed=False)
        if inside and was:
            self._fire()

    def _fire(self):
        if not self.disabled:
            self.activate()

    def _retheme(self, p):
        try:
            self.configure(background=p[_surface(self.on_card)])
            self.redraw()
        except tk.TclError:
            pass

    def activate(self):  # override
        pass

    def redraw(self):  # override
        pass


class Button(_Control):
    def __init__(self, master, app, text: str, command=None, kind: str = "normal", on_card: bool = False,
                 small: bool = False, min_width: int = 0):
        self.text, self.command, self.kind = text, command, kind
        self.font = app.f_small if small else (app.f_strong if kind == "accent" else app.f_body)
        self.padx = app.px(12 if small else 16)
        pady = app.px(5 if small else 8)
        w = max(self.font.measure(text) + 2 * self.padx, app.px(min_width))
        h = self.font.metrics("linespace") + 2 * pady
        super().__init__(master, app, on_card, w, h)

    def configure_text(self, text: str):
        self.text = text
        self.configure(width=max(self.font.measure(text) + 2 * self.padx, int(self["width"])))
        self.redraw()

    def set_kind(self, kind: str):
        self.kind = kind
        if self.font is not self.app.f_small:
            self.font = self.app.f_strong if kind == "accent" else self.app.f_body
            self.configure(width=max(int(self["width"]), self.font.measure(self.text) + 2 * self.padx))
        self.redraw()

    def activate(self):
        if self.command:
            self.command()

    def redraw(self):
        p = self.app.pal
        self.delete("all")
        w, h = int(self["width"]), int(self["height"])
        r = self.app.px(5)
        if self.kind == "accent":
            fill = p["accent_press"] if self.pressed else p["accent_hover"] if self.hover else p["accent"]
            outline, fg = fill, p["accent_text"]
        else:
            fill = p["btn_press"] if self.pressed else p["btn_hover"] if self.hover else p["btn"]
            outline, fg = p["ctl_border"], p["fg"]
        if self.disabled:
            fill, outline, fg = p["btn_disabled"], p["btn_disabled"], p["subtle"]
        rounded(self, 1, 1, w - 1, h - 1, r, fill=outline)
        rounded(self, 2, 2, w - 2, h - 2, r - 1, fill=fill)
        if self.focused and not self.disabled:
            rounded(self, 0, 0, w, h, r + 1, fill="", outline=p["focus"], width=max(1, self.app.px(1.5)))
        self.create_text(w / 2, h / 2, text=self.text, font=self.font, fill=fg)


class CheckBox(_Control):
    def __init__(self, master, app, variable: tk.BooleanVar, on_card: bool = True, command=None):
        self.var, self.command = variable, command
        s = app.px(20)
        super().__init__(master, app, on_card, s + 2, s + 2)
        self._trace = variable.trace_add("write", lambda *a: self.redraw())

    def activate(self):
        self.var.set(not self.var.get())
        if self.command:
            self.command()

    def redraw(self):
        p = self.app.pal
        self.delete("all")
        s = int(self["width"])
        on = bool(self.var.get())
        r = self.app.px(4)
        if self.disabled:
            rounded(self, 1, 1, s - 1, s - 1, r, fill=p["btn_disabled"], outline=p["ctl_border"])
            return
        if on:
            col = p["accent_hover"] if self.hover else p["accent"]
            rounded(self, 1, 1, s - 1, s - 1, r, fill=col, outline=col)
            k = s / 20
            self.create_line(5.5 * k, 10.5 * k, 8.5 * k, 13.5 * k, 14.5 * k, 6.5 * k, fill=p["accent_text"],
                             width=max(2, self.app.px(2)), capstyle="round", joinstyle="round")
        else:
            rounded(self, 1, 1, s - 1, s - 1, r, fill=p["btn_hover"] if self.hover else p["field"],
                    outline=p["ctl_strong"], width=max(1, self.app.px(1)))
        if self.focused:
            rounded(self, 0, 0, s, s, r + 1, fill="", outline=p["focus"])


class Switch(_Control):
    def __init__(self, master, app, variable: tk.BooleanVar, on_card: bool = True, command=None):
        self.var, self.command = variable, command
        super().__init__(master, app, on_card, app.px(42), app.px(22))
        variable.trace_add("write", lambda *a: self.redraw())

    def activate(self):
        self.var.set(not self.var.get())
        if self.command:
            self.command()

    def redraw(self):
        p = self.app.pal
        self.delete("all")
        w, h = int(self["width"]), int(self["height"])
        on = bool(self.var.get())
        r = (h - 2) / 2
        if self.disabled:
            rounded(self, 1, 1, w - 1, h - 1, r, fill=p["btn_disabled"], outline=p["ctl_border"])
            self.create_oval(5, 5, h - 5, h - 5, fill=p["subtle"], outline="")
            return
        if on:
            col = p["accent_hover"] if self.hover else p["accent"]
            rounded(self, 1, 1, w - 1, h - 1, r, fill=col, outline=col)
            d = h * .62
            x = w - h / 2 - 1
            self.create_oval(x - d / 2, h / 2 - d / 2, x + d / 2, h / 2 + d / 2, fill=p["accent_text"], outline="")
        else:
            rounded(self, 1, 1, w - 1, h - 1, r, fill=p["btn_hover"] if self.hover else p["field"],
                    outline=p["ctl_strong"], width=max(1, self.app.px(1)))
            d = h * .5
            x = h / 2 + 1
            self.create_oval(x - d / 2, h / 2 - d / 2, x + d / 2, h / 2 + d / 2, fill=p["ctl_strong"], outline="")
        if self.focused:
            rounded(self, 0, 0, w, h, r + 1, fill="", outline=p["focus"])


class Radio(_Control):
    def __init__(self, master, app, variable: tk.StringVar, value: str, on_card: bool = True):
        self.var, self.value = variable, value
        s = app.px(20)
        super().__init__(master, app, on_card, s + 2, s + 2)
        variable.trace_add("write", lambda *a: self.redraw())

    def activate(self):
        self.var.set(self.value)

    def redraw(self):
        p = self.app.pal
        self.delete("all")
        s = int(self["width"])
        if self.var.get() == self.value:
            col = p["accent_hover"] if self.hover else p["accent"]
            self.create_oval(1, 1, s - 1, s - 1, fill=col, outline=col)
            k = s * .3
            self.create_oval(k, k, s - k, s - k, fill=p["accent_text"], outline="")
        else:
            self.create_oval(1, 1, s - 1, s - 1, fill=p["btn_hover"] if self.hover else p["field"],
                             outline=p["ctl_strong"], width=max(1, self.app.px(1)))
        if self.focused:
            self.create_oval(0, 0, s, s, outline=p["focus"])
