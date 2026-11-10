"""Update checks against mistri's GitHub Releases.

mistri asks GitHub's public API for the latest release (no account, no data about you is sent beyond a
normal web request), compares versions, and, only if you agree, downloads the new installer. It runs the
installer only if Windows confirms it is validly signed by GITAV, so a tampered download is never run.

The automatic check happens at most once a day when the app opens, and can be turned off.
"""
from __future__ import annotations

import json
import re
import subprocess
import time
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from .. import PUBLISHER, UPDATE_REPO, __version__
from .util import APP_DIR, IS_WINDOWS, Emit, default_emit, load_state, powershell_json, save_state

API = f"https://api.github.com/repos/{UPDATE_REPO}/releases/latest"
RELEASES_PAGE = f"https://github.com/{UPDATE_REPO}/releases"
DAY = 86400


@dataclass
class Release:
    version: str
    name: str
    notes: str
    url: str            # installer download URL
    page: str           # release page
    size: int


def parse_version(v: str) -> tuple[int, ...]:
    nums = re.findall(r"\d+", v or "")
    return tuple(int(n) for n in nums[:4]) or (0,)


def is_newer(remote: str, local: str = __version__) -> bool:
    return parse_version(remote) > parse_version(local)


def latest(timeout: float = 15) -> Optional[Release]:
    """The latest published (non-draft, non-prerelease) release with a Windows installer, or None."""
    req = urllib.request.Request(API, headers={"User-Agent": f"mistri/{__version__}",
                                               "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        rel = json.load(r)
    asset = next((a for a in rel.get("assets", [])
                  if re.match(r"mistri-setup-.*\.exe$", a.get("name", ""))), None)
    if not asset:
        return None
    return Release(version=(rel.get("tag_name") or "").lstrip("vV"), name=rel.get("name") or "",
                   notes=(rel.get("body") or "").strip(), url=asset["browser_download_url"],
                   page=rel.get("html_url") or RELEASES_PAGE, size=int(asset.get("size") or 0))


def check(emit: Emit = default_emit, record: bool = True) -> Optional[Release]:
    """Check now. Returns the newer release, or None if up to date. Raises on network errors."""
    emit(f"Checking for updates (you have mistri {__version__})…")
    rel = latest()
    if record:
        st = load_state()
        st.setdefault("updates", {})["last_check"] = time.time()
        st["updates"]["latest_seen"] = rel.version if rel else None
        save_state(st)
    if rel and is_newer(rel.version):
        emit(f"mistri {rel.version} is available.")
        return rel
    emit("mistri is up to date.")
    return None


def auto_enabled() -> bool:
    return load_state().get("updates", {}).get("auto", True)


def set_auto(on: bool) -> None:
    st = load_state()
    st.setdefault("updates", {})["auto"] = bool(on)
    save_state(st)


def due() -> bool:
    """True if the once-a-day automatic check should run now."""
    u = load_state().get("updates", {})
    return u.get("auto", True) and time.time() - u.get("last_check", 0) > DAY


def verify_signature(path: Path) -> tuple[bool, str]:
    """Valid Authenticode signature whose signer is the publisher (GITAV)."""
    if not IS_WINDOWS:
        return False, "signature checks need Windows"
    p = str(path).replace("'", "''")
    rows = powershell_json(f"$s = Get-AuthenticodeSignature -LiteralPath '{p}'; "
                           "[pscustomobject]@{ Status=[string]$s.Status; Subject=[string]$s.SignerCertificate.Subject } "
                           "| ConvertTo-Json", timeout=60)
    if not rows:
        return False, "could not read the signature"
    status, subject = rows[0].get("Status", ""), rows[0].get("Subject", "")
    cn = next((part.strip()[3:] for part in subject.split(",") if part.strip().upper().startswith("CN=")), subject)
    ok = status == "Valid" and PUBLISHER.lower() in subject.lower()
    return ok, f"{status}, signed by {cn or 'nobody'}"


def download_and_verify(rel: Release, emit: Emit = default_emit) -> Optional[Path]:
    from .malware import download_resumable
    folder = APP_DIR / "downloads"
    folder.mkdir(parents=True, exist_ok=True)
    dest = folder / rel.url.rsplit("/", 1)[-1]
    emit(f"Downloading mistri {rel.version} ({rel.size / 2**20:.0f} MB)…")
    if not download_resumable(rel.url, dest, emit):
        emit("[error] The download kept stalling. Try again later, or get it from " + rel.page)
        return None
    if rel.size and dest.stat().st_size != rel.size:
        emit("[error] The download is incomplete; it was deleted. Try again.")
        dest.unlink(missing_ok=True)
        return None
    ok, info = verify_signature(dest)
    if not ok:
        emit(f"[error] The installer isn't validly signed by {PUBLISHER} ({info}). It was deleted, not run.")
        dest.unlink(missing_ok=True)
        return None
    emit(f"Signature verified: {info}.")
    return dest


def launch_installer(path: Path, emit: Emit = default_emit) -> bool:
    """Start the installer (it shows its own steps) so mistri can close and be replaced."""
    if not IS_WINDOWS:
        return False
    import os
    os.startfile(str(path))  # type: ignore[attr-defined]
    emit("The installer is open. mistri will close so it can be updated.")
    return True


def install(emit: Emit = default_emit) -> int:
    """CLI: check, download, verify and launch. 0 = launched or already current."""
    rel = check(emit)
    if not rel:
        return 0
    path = download_and_verify(rel, emit)
    if not path:
        return 1
    return 0 if launch_installer(path, emit) else 1


def open_releases_page() -> None:
    if IS_WINDOWS:
        import os
        os.startfile(RELEASES_PAGE)  # type: ignore[attr-defined]
    else:
        subprocess.Popen(["xdg-open", RELEASES_PAGE])
