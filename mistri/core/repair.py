"""System repair using Windows' own tools: SFC, DISM, CHKDSK, plus winget for app updates.

These replace "registry cleaning": they fix actual corruption in Windows
system files and the component store, which is what causes real problems.
"""
from __future__ import annotations

import os

from .util import Emit, default_emit, powershell, require_admin, run


def restore_point(description: str = "mistri", emit: Emit = default_emit) -> int:
    require_admin("Create restore point")
    emit("Creating a System Restore point (Windows allows one every 24 hours; if one exists it is reused)...")
    # Make sure System Restore is on for the system drive, then checkpoint.
    drive = os.environ.get("SystemDrive", "C:") + "\\"
    return powershell(
        f"Enable-ComputerRestore -Drive '{drive}' -ErrorAction SilentlyContinue; "
        f"Checkpoint-Computer -Description '{description}' -RestorePointType MODIFY_SETTINGS -ErrorAction Stop; "
        "'Restore point ready.'",
        emit)


def sfc(emit: Emit = default_emit) -> int:
    require_admin("System File Checker")
    emit("Running System File Checker (sfc /scannow) — 5 to 20 minutes...")
    return run(["sfc", "/scannow"], emit)


def dism_scan(emit: Emit = default_emit) -> int:
    require_admin("DISM ScanHealth")
    emit("Checking the Windows component store (DISM /ScanHealth)...")
    return run(["DISM", "/Online", "/Cleanup-Image", "/ScanHealth"], emit)


def dism_restore(emit: Emit = default_emit) -> int:
    require_admin("DISM RestoreHealth")
    emit("Repairing the Windows component store (DISM /RestoreHealth) — can take 10-30 minutes...")
    return run(["DISM", "/Online", "/Cleanup-Image", "/RestoreHealth"], emit)


def component_cleanup(emit: Emit = default_emit) -> int:
    require_admin("Component cleanup")
    emit("Removing superseded Windows update components (DISM /StartComponentCleanup)...")
    return run(["DISM", "/Online", "/Cleanup-Image", "/StartComponentCleanup"], emit)


def chkdsk_scan(drive: str | None = None, emit: Emit = default_emit) -> int:
    require_admin("Disk check")
    drive = drive or os.environ.get("SystemDrive", "C:")
    emit(f"Online disk check of {drive} (read-only scan, no reboot)...")
    rc = run(["chkdsk", drive, "/scan"], emit)
    if rc not in (0,):
        emit("If problems were found, run 'chkdsk /spotfix' from an admin prompt; it may need a reboot.")
    return rc


def disk_health(emit: Emit = default_emit) -> int:
    emit("Physical disk health (from the drive's own reporting):")
    return powershell(
        "Get-PhysicalDisk | ForEach-Object { $r = $_ | Get-StorageReliabilityCounter -ErrorAction SilentlyContinue; "
        "[pscustomobject]@{ Disk=$_.FriendlyName; Type=$_.MediaType; Health=$_.HealthStatus; "
        "Status=$_.OperationalStatus; SizeGB=[math]::Round($_.Size/1GB); TempC=$r.Temperature; "
        "Wear=$r.Wear; ReadErrors=$r.ReadErrorsUncorrected; PowerOnHours=$r.PowerOnHours } } | Format-List",
        emit)


def full_repair(emit: Emit = default_emit) -> int:
    """Restore point -> DISM RestoreHealth -> SFC. The standard order: DISM fixes
    the store that SFC repairs from."""
    require_admin("Full repair")
    restore_point("mistri full repair", emit)
    rc = dism_restore(emit)
    if rc != 0:
        emit("DISM did not finish cleanly; running SFC anyway.")
    rc2 = sfc(emit)
    emit("Full repair finished. Restart the PC if SFC reported repairs.")
    return rc or rc2


# ------------------------------------------------------------------ app updates (winget)

def updates_list(emit: Emit = default_emit) -> int:
    emit("Checking for app updates with winget...")
    return run(["winget", "upgrade", "--include-unknown", "--accept-source-agreements"], emit)


def updates_apply(emit: Emit = default_emit) -> int:
    emit("Installing all available app updates with winget (installers may show their own prompts)...")
    return run(["winget", "upgrade", "--all", "--silent", "--accept-source-agreements",
                "--accept-package-agreements", "--disable-interactivity"], emit)
