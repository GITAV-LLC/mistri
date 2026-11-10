"""Startup apps and scheduled tasks.

Startup items are enabled/disabled the same way Task Manager does it (the
StartupApproved registry keys), so nothing is deleted and every change can be
undone here or in Task Manager > Startup apps.
"""
from __future__ import annotations

import struct
import time
from dataclasses import dataclass

from .util import IS_WINDOWS, Emit, NeedsAdmin, default_emit, env_path, is_admin, powershell, powershell_json

if IS_WINDOWS:
    import winreg
else:  # allows importing on other OSes for testing
    winreg = None  # type: ignore

RUN = r"Software\Microsoft\Windows\CurrentVersion\Run"
RUN32 = r"Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Run"
APPROVED = r"Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved"


@dataclass
class StartupItem:
    name: str
    command: str
    location: str        # human-readable
    hive: str            # "HKCU" | "HKLM"
    approved_subkey: str  # Run | Run32 | StartupFolder
    enabled: bool

    @property
    def needs_admin(self) -> bool:
        return self.hive == "HKLM"

    @property
    def id(self) -> str:
        return f"{self.hive}:{self.approved_subkey}:{self.name}"


def _hive(h: str):
    return winreg.HKEY_CURRENT_USER if h == "HKCU" else winreg.HKEY_LOCAL_MACHINE


def _read_values(hive: str, path: str) -> dict[str, object]:
    out: dict[str, object] = {}
    try:
        with winreg.OpenKey(_hive(hive), path, 0, winreg.KEY_READ | winreg.KEY_WOW64_64KEY) as k:
            i = 0
            while True:
                try:
                    name, val, _ = winreg.EnumValue(k, i)
                except OSError:
                    break
                out[name] = val
                i += 1
    except OSError:
        pass
    return out


def _approved_state(hive: str, sub: str, name: str) -> bool:
    vals = _read_values(hive, f"{APPROVED}\\{sub}")
    v = vals.get(name)
    if isinstance(v, (bytes, bytearray)) and v:
        return (v[0] & 1) == 0   # 02/06 = enabled, 03/07 = disabled
    return True


def list_startup() -> list[StartupItem]:
    if not IS_WINDOWS:
        return []
    items: list[StartupItem] = []
    for hive, path, sub, label in (("HKCU", RUN, "Run", "Registry (you)"),
                                   ("HKLM", RUN, "Run", "Registry (all users)"),
                                   ("HKLM", RUN32, "Run32", "Registry (all users, 32-bit)")):
        for name, cmd in _read_values(hive, path).items():
            if not name:
                continue
            items.append(StartupItem(name, str(cmd), label, hive, sub, _approved_state(hive, sub, name)))
    for hive, folder, label in (
            ("HKCU", env_path("APPDATA", "Microsoft", "Windows", "Start Menu", "Programs", "Startup"),
             "Startup folder (you)"),
            ("HKLM", env_path("PROGRAMDATA", "Microsoft", "Windows", "Start Menu", "Programs", "StartUp"),
             "Startup folder (all users)")):
        if folder and folder.exists():
            for f in folder.iterdir():
                if f.name.lower() == "desktop.ini":
                    continue
                items.append(StartupItem(f.name, str(f), label, hive, "StartupFolder",
                                         _approved_state(hive, "StartupFolder", f.name)))
    return sorted(items, key=lambda i: i.name.lower())


def set_startup(item: StartupItem, enabled: bool, emit: Emit = default_emit) -> None:
    if item.needs_admin and not is_admin():
        raise NeedsAdmin(f"Changing '{item.name}' affects all users and needs administrator rights.")
    if enabled:
        data = bytes([0x02]) + bytes(11)
    else:
        # 03 00 00 00 + FILETIME of when it was disabled (what Task Manager writes)
        filetime = int((time.time() + 11644473600) * 10_000_000)
        data = bytes([0x03, 0, 0, 0]) + struct.pack("<Q", filetime)
    key = winreg.CreateKeyEx(_hive(item.hive), f"{APPROVED}\\{item.approved_subkey}", 0,
                             winreg.KEY_SET_VALUE | winreg.KEY_WOW64_64KEY)
    with key:
        winreg.SetValueEx(key, item.name, 0, winreg.REG_BINARY, data)
    item.enabled = enabled
    emit(f"{'Enabled' if enabled else 'Disabled'} startup item: {item.name}")


# ------------------------------------------------------------------ scheduled tasks

def list_tasks(include_microsoft: bool = False) -> list[dict]:
    """Scheduled tasks outside \\Microsoft\\ — where most third-party (and malware) persistence lives."""
    flt = "" if include_microsoft else "| Where-Object { $_.TaskPath -notlike '\\Microsoft\\*' }"
    return powershell_json(
        f"Get-ScheduledTask {flt} | ForEach-Object {{ [pscustomobject]@{{ Name=$_.TaskName; Path=$_.TaskPath; "
        "State=[string]$_.State; Author=$_.Author; "
        "Action=(($_.Actions | ForEach-Object { ($_.Execute + ' ' + $_.Arguments).Trim() }) -join ' | ') }} }} "
        "| ConvertTo-Json -Depth 3")


def set_task(path: str, name: str, enabled: bool, emit: Emit = default_emit) -> int:
    verb = "Enable-ScheduledTask" if enabled else "Disable-ScheduledTask"
    p = path.replace("'", "''")
    n = name.replace("'", "''")
    return powershell(f"{verb} -TaskPath '{p}' -TaskName '{n}' | Out-Null; '{verb.split('-')[0]}d task: {n}'", emit)
