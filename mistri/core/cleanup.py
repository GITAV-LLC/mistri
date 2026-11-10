"""Junk-file cleanup with a preview step.

Nothing is deleted until you've seen what a target holds. Rules that keep it safe:
  * never follows symlinks or junctions (reparse points) out of a target folder
  * temp folders only lose files older than `min_age_hours` (default 24h)
  * browser caches are skipped while that browser is running
  * files that are locked or in use are skipped, not forced
"""
from __future__ import annotations

import ctypes
import glob
import os
import stat
import subprocess
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Optional

from .util import IS_WINDOWS, Emit, NeedsAdmin, default_emit, env_path, existing, human, is_admin, run

FILE_ATTRIBUTE_REPARSE_POINT = 0x400


@dataclass
class Target:
    key: str
    name: str
    description: str
    paths: Callable[[], list[Path]]
    admin: bool = False
    min_age_hours: float = 0
    default: bool = True
    browser_proc: Optional[str] = None   # skip while this process is running
    special: Optional[str] = None        # "recycle" | "wu"
    files_only_glob: bool = False        # paths() returns files, not folders


@dataclass
class ScanResult:
    target: Target
    files: list[tuple[Path, int]] = field(default_factory=list)
    roots: list[Path] = field(default_factory=list)
    skipped_reason: str = ""

    @property
    def size(self) -> int:
        return sum(s for _, s in self.files)


# ------------------------------------------------------------------ targets

def _glob(pattern: Optional[Path]) -> list[Path]:
    if pattern is None:
        return []
    return [Path(p) for p in glob.glob(str(pattern))]


def _chromium_cache(vendor_path: tuple[str, ...]) -> Callable[[], list[Path]]:
    def f() -> list[Path]:
        base = env_path("LOCALAPPDATA", *vendor_path, "User Data")
        if not base or not base.exists():
            return []
        out: list[Path] = []
        for prof in [base / "Default", *base.glob("Profile *"), base / "Guest Profile"]:
            for sub in ("Cache", "Code Cache", "GPUCache", "Service Worker/CacheStorage"):
                p = prof / sub
                if p.exists():
                    out.append(p)
        for sub in ("ShaderCache", "GrShaderCache"):
            if (base / sub).exists():
                out.append(base / sub)
        return out
    return f


def _firefox_cache() -> list[Path]:
    return _glob(env_path("LOCALAPPDATA", "Mozilla", "Firefox", "Profiles", "*", "cache2"))


TARGETS: list[Target] = [
    Target("user_temp", "Your temp files", "Files in %TEMP% older than a day",
           lambda: existing([env_path("TEMP")]), min_age_hours=24),
    Target("windows_temp", "Windows temp files", "C:\\Windows\\Temp, older than a day",
           lambda: existing([env_path("WINDIR", "Temp")]), admin=True, min_age_hours=24),
    Target("chrome_cache", "Chrome cache", "Page/code/GPU caches (skipped while Chrome is open)",
           _chromium_cache(("Google", "Chrome")), browser_proc="chrome.exe"),
    Target("edge_cache", "Edge cache", "Page/code/GPU caches (skipped while Edge is open)",
           _chromium_cache(("Microsoft", "Edge")), browser_proc="msedge.exe"),
    Target("brave_cache", "Brave cache", "Page/code/GPU caches (skipped while Brave is open)",
           _chromium_cache(("BraveSoftware", "Brave-Browser")), browser_proc="brave.exe"),
    Target("firefox_cache", "Firefox cache", "cache2 folders (skipped while Firefox is open)",
           _firefox_cache, browser_proc="firefox.exe"),
    Target("thumbnails", "Thumbnail cache", "Explorer thumbnail databases (rebuilt automatically)",
           lambda: _glob(env_path("LOCALAPPDATA", "Microsoft", "Windows", "Explorer", "thumbcache_*.db")),
           files_only_glob=True),
    Target("crash_dumps", "Crash dumps", "App crash dumps in your profile",
           lambda: existing([env_path("LOCALAPPDATA", "CrashDumps")])),
    Target("error_reports", "Error reports", "Windows Error Reporting archive and queue",
           lambda: existing([env_path("PROGRAMDATA", "Microsoft", "Windows", "WER", "ReportArchive"),
                             env_path("PROGRAMDATA", "Microsoft", "Windows", "WER", "ReportQueue")]),
           admin=True),
    Target("minidumps", "System minidumps", "C:\\Windows\\Minidump (only useful for BSOD debugging)",
           lambda: existing([env_path("WINDIR", "Minidump")]), admin=True, default=False),
    Target("wu_downloads", "Windows Update downloads",
           "Already-installed update packages; Windows re-downloads anything it still needs",
           lambda: existing([env_path("WINDIR", "SoftwareDistribution", "Download")]),
           admin=True, default=False, special="wu"),
    Target("recycle_bin", "Recycle Bin", "Empties the Recycle Bin on all drives",
           lambda: [], default=False, special="recycle"),
]

TARGETS_BY_KEY = {t.key: t for t in TARGETS}


# ------------------------------------------------------------------ helpers

def _running(proc_name: str) -> bool:
    try:
        import psutil
        name = proc_name.lower()
        return any((p.info.get("name") or "").lower() == name for p in psutil.process_iter(["name"]))
    except ImportError:
        if not IS_WINDOWS:
            return False
        out = subprocess.run(["tasklist", "/FI", f"IMAGENAME eq {proc_name}"], capture_output=True,
                             text=True, creationflags=0x08000000).stdout
        return proc_name.lower() in out.lower()


def _is_reparse(entry: os.DirEntry) -> bool:
    try:
        if entry.is_symlink():
            return True
        st = entry.stat(follow_symlinks=False)
        return bool(getattr(st, "st_file_attributes", 0) & FILE_ATTRIBUTE_REPARSE_POINT)
    except OSError:
        return True


def _walk_files(root: Path, cutoff: float) -> list[tuple[Path, int]]:
    out: list[tuple[Path, int]] = []
    stack = [root]
    while stack:
        d = stack.pop()
        try:
            with os.scandir(d) as it:
                for e in it:
                    if _is_reparse(e):
                        continue
                    try:
                        if e.is_dir(follow_symlinks=False):
                            stack.append(Path(e.path))
                        elif e.is_file(follow_symlinks=False):
                            st = e.stat(follow_symlinks=False)
                            if st.st_mtime <= cutoff:
                                out.append((Path(e.path), st.st_size))
                    except OSError:
                        continue
        except OSError:
            continue
    return out


class _SHQUERYRBINFO(ctypes.Structure):
    _fields_ = [("cbSize", ctypes.c_ulong), ("i64Size", ctypes.c_longlong), ("i64NumItems", ctypes.c_longlong)]


def recycle_bin_size() -> tuple[int, int]:
    if not IS_WINDOWS:
        return 0, 0
    info = _SHQUERYRBINFO()
    info.cbSize = ctypes.sizeof(info)
    if ctypes.windll.shell32.SHQueryRecycleBinW(None, ctypes.byref(info)) != 0:
        return 0, 0
    return int(info.i64Size), int(info.i64NumItems)


def empty_recycle_bin() -> bool:
    if not IS_WINDOWS:
        return False
    SHERB_NOCONFIRMATION, SHERB_NOPROGRESSUI, SHERB_NOSOUND = 0x1, 0x2, 0x4
    rc = ctypes.windll.shell32.SHEmptyRecycleBinW(None, None,
                                                  SHERB_NOCONFIRMATION | SHERB_NOPROGRESSUI | SHERB_NOSOUND)
    # 0x8000FFFF (E_UNEXPECTED) is returned when the bin is already empty
    return rc in (0, -2147418113)


# ------------------------------------------------------------------ public API

def scan(keys: Optional[list[str]] = None, emit: Emit = default_emit) -> list[ScanResult]:
    """Work out what each target would remove. Deletes nothing."""
    targets = [TARGETS_BY_KEY[k] for k in keys] if keys else [t for t in TARGETS if t.default]
    admin = is_admin()
    results: list[ScanResult] = []
    for t in targets:
        r = ScanResult(t)
        if t.admin and not admin:
            r.skipped_reason = "needs administrator"
        elif t.browser_proc and _running(t.browser_proc):
            r.skipped_reason = f"close {t.browser_proc.removesuffix('.exe').title()} first"
        elif t.special == "recycle":
            size, count = recycle_bin_size()
            r.files = [(Path("Recycle Bin"), size)] if count else []
        else:
            cutoff = time.time() - t.min_age_hours * 3600
            roots = t.paths()
            r.roots = roots
            for root in roots:
                if t.files_only_glob:
                    try:
                        st = root.stat()
                        if st.st_mtime <= cutoff:
                            r.files.append((root, st.st_size))
                    except OSError:
                        pass
                else:
                    r.files.extend(_walk_files(root, cutoff))
        note = f"skipped ({r.skipped_reason})" if r.skipped_reason else f"{len(r.files):,} files, {human(r.size)}"
        emit(f"  {t.name:<28} {note}")
        results.append(r)
    total = sum(r.size for r in results)
    emit(f"Total reclaimable: {human(total)}")
    return results


def _remove_empty_dirs(root: Path) -> None:
    """Remove empty subfolders under root (never root itself)."""
    for dirpath, dirnames, _ in os.walk(root, topdown=False, followlinks=False):
        if Path(dirpath) == root:
            continue
        try:
            os.rmdir(dirpath)
        except OSError:
            pass


def clean(results: list[ScanResult], emit: Emit = default_emit) -> tuple[int, int]:
    """Delete what `scan` found. Returns (files removed, bytes freed)."""
    total_files = total_bytes = 0
    for r in results:
        t = r.target
        if r.skipped_reason or not r.files:
            continue
        if t.admin and not is_admin():
            raise NeedsAdmin(f"Cleaning '{t.name}' needs administrator rights.")
        if t.special == "recycle":
            size = r.size
            if empty_recycle_bin():
                emit(f"  Emptied Recycle Bin ({human(size)})")
                total_bytes += size
            continue
        if t.special == "wu":
            run(["net", "stop", "wuauserv"], lambda _: None)
            run(["net", "stop", "bits"], lambda _: None)
        removed = freed = locked = 0
        for path, size in r.files:
            try:
                os.remove(path)
            except PermissionError:
                try:
                    os.chmod(path, stat.S_IWRITE)
                    os.remove(path)
                except OSError:
                    locked += 1
                    continue
            except FileNotFoundError:
                continue
            except OSError:
                locked += 1
                continue
            removed += 1
            freed += size
        if not t.files_only_glob:
            for root in r.roots:
                _remove_empty_dirs(root)
        if t.special == "wu":
            run(["net", "start", "bits"], lambda _: None)
            run(["net", "start", "wuauserv"], lambda _: None)
        extra = f", {locked:,} in use (left alone)" if locked else ""
        emit(f"  {t.name:<28} removed {removed:,} files, {human(freed)}{extra}")
        total_files += removed
        total_bytes += freed
    emit(f"Freed {human(total_bytes)} ({total_files:,} files).")
    return total_files, total_bytes
