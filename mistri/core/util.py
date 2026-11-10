"""Shared helpers: admin checks, running external tools, logging, state."""
from __future__ import annotations

import codecs
import ctypes
import datetime as _dt
import json
import logging
import os
import subprocess
import sys
from pathlib import Path
from typing import Callable, Iterable, Optional, Sequence

IS_WINDOWS = os.name == "nt"

APP_DIR = Path(os.environ.get("LOCALAPPDATA") or Path.home() / ".local" / "share") / "mistri"
LOG_DIR = APP_DIR / "logs"
QUARANTINE_DIR = APP_DIR / "quarantine"
STATE_FILE = APP_DIR / "state.json"

Emit = Callable[[str], None]


def default_emit(msg: str) -> None:
    print(msg, flush=True)


def ensure_dirs() -> None:
    first_run = not APP_DIR.exists()
    for d in (APP_DIR, LOG_DIR, QUARANTINE_DIR):
        d.mkdir(parents=True, exist_ok=True)
    # Carry settings and undo history over from the earlier "PCMaint" name.
    old = APP_DIR.parent / "PCMaint" / "state.json"
    if first_run and old.exists() and not STATE_FILE.exists():
        try:
            STATE_FILE.write_bytes(old.read_bytes())
        except OSError:
            pass


def get_logger() -> logging.Logger:
    ensure_dirs()
    log = logging.getLogger("mistri")
    if not log.handlers:
        log.setLevel(logging.INFO)
        fh = logging.FileHandler(LOG_DIR / f"mistri-{_dt.date.today():%Y-%m-%d}.log", encoding="utf-8")
        fh.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
        log.addHandler(fh)
    return log


def logged(emit: Emit) -> Emit:
    """Wrap an emitter so every line is also written to the daily log file."""
    log = get_logger()

    def _e(msg: str) -> None:
        log.info(msg)
        emit(msg)

    return _e


# ---------------------------------------------------------------- admin

def is_admin() -> bool:
    if not IS_WINDOWS:
        return os.geteuid() == 0 if hasattr(os, "geteuid") else False
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def relaunch_as_admin(extra_args: Sequence[str] = ()) -> bool:
    """Restart this program elevated (UAC prompt). Returns True if launched."""
    if not IS_WINDOWS:
        return False
    exe = sys.executable
    # Prefer pythonw for the GUI so no console window appears.
    if not extra_args or extra_args[0] == "gui":
        w = Path(exe).with_name("pythonw.exe")
        if w.exists():
            exe = str(w)
    params = subprocess.list2cmdline(["-m", "mistri", *extra_args])
    cwd = str(Path(__file__).resolve().parents[2])
    rc = ctypes.windll.shell32.ShellExecuteW(None, "runas", exe, params, cwd, 1)
    return rc > 32


class NeedsAdmin(RuntimeError):
    pass


def require_admin(what: str) -> None:
    if not is_admin():
        raise NeedsAdmin(f"'{what}' needs administrator rights. Restart mistri as administrator.")


# ---------------------------------------------------------------- formatting

def human(n: float) -> str:
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if abs(n) < 1024 or unit == "TB":
            return f"{n:.0f} {unit}" if unit == "B" else f"{n:.1f} {unit}"
        n /= 1024
    return f"{n:.1f} TB"


# ---------------------------------------------------------------- running tools

_NO_WINDOW = 0x08000000 if IS_WINDOWS else 0


def run(cmd: Sequence[str] | str, emit: Optional[Emit] = None, *, timeout: Optional[float] = None,
        quiet_repeats: bool = True, cwd: Optional[str] = None) -> int:
    """Run a command, streaming its output line by line to `emit`.

    Handles Windows tools that write UTF-16 (sfc) and progress bars that use
    carriage returns (DISM), so the output stays readable in the GUI.
    """
    emit = emit or default_emit
    try:
        proc = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
            creationflags=_NO_WINDOW, cwd=cwd, shell=isinstance(cmd, str),
        )
    except FileNotFoundError:
        name = cmd if isinstance(cmd, str) else cmd[0]
        emit(f"[error] Not found: {name}")
        return 127

    assert proc.stdout is not None
    decoder = None
    buf = ""
    last = None

    def flush_line(line: str) -> None:
        nonlocal last
        line = line.strip().replace("\x00", "")
        if not line:
            return
        if quiet_repeats and line == last:
            return
        last = line
        emit(line)

    try:
        while True:
            chunk = proc.stdout.read1(4096) if hasattr(proc.stdout, "read1") else proc.stdout.read(4096)
            if not chunk:
                break
            if decoder is None:
                # sfc.exe and a few others write UTF-16LE when piped.
                looks_utf16 = len(chunk) > 3 and chunk[1:2] == b"\x00" and chunk[3:4] == b"\x00"
                enc = "utf-16-le" if looks_utf16 else ("oem" if IS_WINDOWS else "utf-8")
                try:
                    decoder = codecs.getincrementaldecoder(enc)(errors="replace")
                except LookupError:
                    decoder = codecs.getincrementaldecoder("utf-8")(errors="replace")
            buf += decoder.decode(chunk)
            while True:
                idx = min([i for i in (buf.find("\n"), buf.find("\r")) if i != -1], default=-1)
                if idx == -1:
                    break
                flush_line(buf[:idx])
                buf = buf[idx + 1:]
        flush_line(buf)
        return proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        proc.kill()
        emit("[error] Timed out")
        return -1


def capture(cmd: Sequence[str] | str, timeout: Optional[float] = 120) -> tuple[int, str]:
    """Run a command and return (returncode, combined output)."""
    try:
        p = subprocess.run(cmd, capture_output=True, timeout=timeout, creationflags=_NO_WINDOW,
                           shell=isinstance(cmd, str), stdin=subprocess.DEVNULL)
    except FileNotFoundError:
        return 127, ""
    except subprocess.TimeoutExpired:
        return -1, "timed out"
    raw = p.stdout + p.stderr
    for enc in ("utf-8", "utf-16-le", "mbcs" if IS_WINDOWS else "latin-1"):
        try:
            return p.returncode, raw.decode(enc)
        except (UnicodeDecodeError, LookupError):
            continue
    return p.returncode, raw.decode("utf-8", "replace")


def powershell(script: str, emit: Optional[Emit] = None) -> int:
    return run(["powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-Command", script], emit)


def powershell_json(script: str, timeout: float = 120) -> list[dict]:
    """Run PowerShell that ends in ConvertTo-Json and return a list of dicts."""
    rc, out = capture(["powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                       "-Command", f"[Console]::OutputEncoding=[Text.Encoding]::UTF8; {script}"], timeout)
    out = out.strip()
    if rc != 0 or not out:
        return []
    try:
        data = json.loads(out)
    except json.JSONDecodeError:
        return []
    return data if isinstance(data, list) else [data]


# ---------------------------------------------------------------- state (undo info)

def load_state() -> dict:
    try:
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except Exception:
        return {}


def save_state(state: dict) -> None:
    ensure_dirs()
    tmp = STATE_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(state, indent=2), encoding="utf-8")
    tmp.replace(STATE_FILE)


def env_path(var: str, *parts: str) -> Optional[Path]:
    base = os.environ.get(var)
    return Path(base, *parts) if base else None


def existing(paths: Iterable[Optional[Path]]) -> list[Path]:
    return [p for p in paths if p is not None and p.exists()]
