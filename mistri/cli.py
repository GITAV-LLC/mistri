"""Command-line interface. Run `python -m mistri --help`.
With no arguments, the GUI opens.
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from .core import cleanup, malware, repair, resources, startup, updates
from .core.util import NeedsAdmin, human, is_admin, logged, default_emit, run, relaunch_as_admin

TASK_NAME = "mistri Weekly Clean"


def _emit():
    return logged(default_emit)


def cmd_clean(a) -> int:
    e = _emit()
    keys = None
    if a.all:
        keys = [t.key for t in cleanup.TARGETS]
    elif a.targets:
        keys = [k.strip() for k in a.targets.split(",")]
        bad = [k for k in keys if k not in cleanup.TARGETS_BY_KEY]
        if bad:
            print("Unknown target(s): " + ", ".join(bad) + "\nRun 'clean --list' to see them.")
            return 2
    e("Analyzing...")
    results = cleanup.scan(keys, e)
    if a.preview:
        return 0
    if not any(r.files and not r.skipped_reason for r in results):
        e("Nothing to clean.")
        return 0
    if not a.yes:
        if input("Delete these files? [y/N] ").strip().lower() != "y":
            print("Cancelled.")
            return 1
    cleanup.clean(results, e)
    return 0


def cmd_clean_list(_a) -> int:
    for t in cleanup.TARGETS:
        flags = []
        if t.default:
            flags.append("default")
        if t.admin:
            flags.append("admin")
        print(f"  {t.key:<15} {t.name:<26} {t.description}" + (f"  [{', '.join(flags)}]" if flags else ""))
    return 0


def cmd_scan(a) -> int:
    e = _emit()
    rc = 0
    if a.engine in ("defender", "both"):
        kind = "custom" if a.path else ("full" if a.full else "quick")
        rc |= malware.defender_scan(kind, a.path, e) not in (0,)
    if a.engine in ("clamav", "both"):
        paths = [a.path] if a.path else None
        if a.full and not a.path:
            paths = [str(Path.home())]
        rc |= malware.clamav_scan(paths, a.quarantine, e) not in (0,)
    return int(rc)


def cmd_update_defs(a) -> int:
    e = _emit()
    r1 = malware.defender_update(e) if a.engine in ("defender", "both") else 0
    r2 = malware.clamav_update(e) if a.engine in ("clamav", "both") else 0
    return 0 if r1 == 0 and r2 == 0 else 1


def cmd_threats(_a) -> int:
    return malware.defender_threats(_emit())


def cmd_repair(a) -> int:
    e = _emit()
    fn = {"sfc": repair.sfc, "dism-scan": repair.dism_scan, "dism": repair.dism_restore,
          "component-cleanup": repair.component_cleanup, "chkdsk": repair.chkdsk_scan,
          "disk-health": repair.disk_health, "full": repair.full_repair,
          "restore-point": repair.restore_point}[a.action]
    return fn(emit=e) or 0


def cmd_updates(a) -> int:
    e = _emit()
    return repair.updates_apply(e) if a.apply else repair.updates_list(e)


def cmd_startup(a) -> int:
    items = startup.list_startup()
    if a.action == "list":
        for i, it in enumerate(items, 1):
            print(f"{i:>3}. [{'on ' if it.enabled else 'off'}] {it.name:<32} {it.location:<28} {it.command[:70]}")
        if not items:
            print("No startup items found (or not running on Windows).")
        return 0
    try:
        it = items[int(a.number) - 1]
    except (ValueError, IndexError):
        print("Give the number shown by 'startup list'.")
        return 2
    startup.set_startup(it, a.action == "enable", _emit())
    return 0


def cmd_tasks(a) -> int:
    tasks = startup.list_tasks(a.all)
    if a.action == "list":
        for i, t in enumerate(tasks, 1):
            print(f"{i:>3}. [{t.get('State', ''):<8}] {t.get('Path', '')}{t.get('Name', '')}\n       {t.get('Action', '')[:110]}")
        return 0
    try:
        t = tasks[int(a.number) - 1]
    except (ValueError, IndexError, TypeError):
        print("Give the number shown by 'tasks list'.")
        return 2
    return startup.set_task(t["Path"], t["Name"], a.action == "enable", _emit())


def cmd_services(a) -> int:
    e = _emit()
    if a.action == "list":
        for s in resources.list_services(a.filter or "", a.third_party):
            lock = " (protected)" if s.protected else ""
            print(f"  {s.name:<32} {s.status:<9} {s.start:<9} {s.display[:50]}{lock}")
        changed = resources.changed_services()
        if changed:
            print("\nChanged by mistri (original start type): " +
                  ", ".join(f"{k}={v}" for k, v in changed.items()))
        return 0
    if a.action == "set":
        return resources.set_service_start(a.name, a.start, e)
    return resources.undo_service(a.name, e)


def cmd_procs(a) -> int:
    e = _emit()
    if a.action == "top":
        print(resources.system_summary())
        for p in resources.top_processes(a.n, a.sort):
            print(f"  {p.pid:>7} {p.name:<34} CPU {p.cpu:5.1f}%  RAM {human(p.mem):>9}")
        return 0
    if a.action == "kill":
        return 0 if resources.kill(a.pid, e) else 1
    resources.set_priority(a.pid, a.level, e)
    return 0


def schedule_weekly(day: str = "SUN", time: str = "03:00", elevated: bool = False, emit=default_emit) -> int:
    """Weekly unattended cleanup via Windows Task Scheduler (default targets only)."""
    pyw = Path(sys.executable).with_name("pythonw.exe")
    exe = pyw if pyw.exists() else Path(sys.executable)
    root = Path(__file__).resolve().parents[1]
    tr = f'cmd /c cd /d "{root}" && "{exe}" -m mistri clean --yes'
    cmd = ["schtasks", "/Create", "/TN", TASK_NAME, "/TR", tr, "/SC", "WEEKLY", "/D", day.upper(),
           "/ST", time, "/F"]
    if elevated:
        cmd += ["/RL", "HIGHEST"]
    rc = run(cmd, emit)
    if rc == 0:
        emit(f"Scheduled: default cleanup every {day.upper()} at {time}. "
             "Remove with 'python -m mistri schedule remove'.")
    return rc


def cmd_schedule(a) -> int:
    if a.action == "remove":
        return run(["schtasks", "/Delete", "/TN", TASK_NAME, "/F"])
    return schedule_weekly(a.day, a.time, a.elevated, _emit())


def cmd_gui(_a) -> int:
    from .gui import main as gui_main
    gui_main()
    return 0


def cmd_elevate(_a) -> int:
    if is_admin():
        print("Already running as administrator.")
        return 0
    return 0 if relaunch_as_admin(["gui"]) else 1


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="mistri", description="Lightweight Windows maintenance toolkit.")
    sub = p.add_subparsers(dest="cmd")

    s = sub.add_parser("clean", help="Remove junk files (shows what it found first)")
    s.add_argument("--preview", action="store_true", help="Only show what would be removed")
    s.add_argument("--targets", help="Comma-separated target keys (see 'clean --list')")
    s.add_argument("--all", action="store_true", help="Every target, including the off-by-default ones")
    s.add_argument("--yes", "-y", action="store_true", help="Don't ask for confirmation")
    s.add_argument("--list", action="store_true", help="List cleanup targets")
    s.set_defaults(func=lambda a: cmd_clean_list(a) if a.list else cmd_clean(a))

    s = sub.add_parser("scan", help="Malware scan with Defender and/or ClamAV")
    s.add_argument("--engine", choices=["defender", "clamav", "both"], default="both")
    s.add_argument("--full", action="store_true", help="Full scan instead of quick")
    s.add_argument("--path", help="Scan just this file or folder")
    s.add_argument("--quarantine", action="store_true", help="ClamAV: move infected files to quarantine")
    s.set_defaults(func=cmd_scan)

    s = sub.add_parser("update-defs", help="Update malware signatures")
    s.add_argument("--engine", choices=["defender", "clamav", "both"], default="both")
    s.set_defaults(func=cmd_update_defs)

    s = sub.add_parser("setup-clamav", help="Download, verify and install ClamAV, then fetch signatures")
    s.add_argument("--no-signatures", action="store_true", help="Skip the ~300 MB signature download")
    s.set_defaults(func=lambda a: malware.install_clamav(_emit(), not a.no_signatures))

    s = sub.add_parser("update", help="Check for a new mistri version (--install to download, verify and run it)")
    s.add_argument("--install", action="store_true")
    s.set_defaults(func=lambda a: updates.install(_emit()) if a.install else (0 if updates.check(_emit()) is None else 10))

    s = sub.add_parser("threats", help="Show Defender's recent detections")
    s.set_defaults(func=cmd_threats)

    s = sub.add_parser("repair", help="Repair Windows with SFC/DISM/CHKDSK")
    s.add_argument("action", choices=["full", "sfc", "dism-scan", "dism", "component-cleanup", "chkdsk",
                                      "disk-health", "restore-point"])
    s.set_defaults(func=cmd_repair)

    s = sub.add_parser("updates", help="List (or --apply) app updates via winget")
    s.add_argument("--apply", action="store_true")
    s.set_defaults(func=cmd_updates)

    s = sub.add_parser("startup", help="List or toggle startup apps")
    s.add_argument("action", choices=["list", "enable", "disable"])
    s.add_argument("number", nargs="?")
    s.set_defaults(func=cmd_startup)

    s = sub.add_parser("tasks", help="List or toggle non-Microsoft scheduled tasks")
    s.add_argument("action", choices=["list", "enable", "disable"])
    s.add_argument("number", nargs="?")
    s.add_argument("--all", action="store_true", help="Include Microsoft's own tasks")
    s.set_defaults(func=cmd_tasks)

    s = sub.add_parser("services", help="List, change or undo service start types")
    s.add_argument("action", choices=["list", "set", "undo"])
    s.add_argument("name", nargs="?")
    s.add_argument("start", nargs="?", choices=["automatic", "delayed", "manual", "disabled"])
    s.add_argument("--filter")
    s.add_argument("--third-party", action="store_true", help="Hide Microsoft services")
    s.set_defaults(func=cmd_services)

    s = sub.add_parser("procs", help="Top processes, end a process, set priority")
    s.add_argument("action", choices=["top", "kill", "priority"])
    s.add_argument("pid", nargs="?", type=int)
    s.add_argument("level", nargs="?", choices=resources.PRIORITIES)
    s.add_argument("-n", type=int, default=25)
    s.add_argument("--sort", choices=["mem", "cpu"], default="mem")
    s.set_defaults(func=cmd_procs)

    s = sub.add_parser("schedule", help="Weekly automatic cleanup via Task Scheduler")
    s.add_argument("action", choices=["weekly", "remove"])
    s.add_argument("--day", default="SUN", choices=["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"])
    s.add_argument("--time", default="03:00")
    s.add_argument("--elevated", action="store_true", help="Run with admin rights (cleans system temp too)")
    s.set_defaults(func=cmd_schedule)

    s = sub.add_parser("gui", help="Open the graphical interface")
    s.set_defaults(func=cmd_gui)
    s = sub.add_parser("elevate", help="Reopen the GUI as administrator")
    s.set_defaults(func=cmd_elevate)
    return p


def main(argv: list[str] | None = None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    # Under pythonw (GUI / scheduled runs) there is no console; keep print() from crashing.
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w")
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w")
    for stream in (sys.stdout, sys.stderr):   # never crash on a character the console can't show
        try:
            stream.reconfigure(errors="replace")
        except (AttributeError, ValueError):
            pass
    parser = build_parser()
    if not argv:
        return cmd_gui(None)
    a = parser.parse_args(argv)
    if not getattr(a, "func", None):
        parser.print_help()
        return 0
    # argument sanity for subcommands with optional positionals
    if a.cmd in ("startup", "tasks") and a.action != "list" and not a.number:
        parser.error(f"{a.cmd} {a.action} needs a number from '{a.cmd} list'")
    if a.cmd == "services" and a.action in ("set", "undo") and not a.name:
        parser.error("services set/undo needs a service name")
    if a.cmd == "services" and a.action == "set" and not a.start:
        parser.error("services set NAME needs automatic|delayed|manual|disabled")
    if a.cmd == "procs" and a.action in ("kill", "priority") and a.pid is None:
        parser.error("needs a PID")
    if a.cmd == "procs" and a.action == "priority" and not a.level:
        parser.error("needs a priority level")
    try:
        return int(a.func(a) or 0)
    except NeedsAdmin as ex:
        print(f"[admin needed] {ex}\nOpen an administrator terminal, or run: python -m mistri elevate")
        return 5
    except KeyboardInterrupt:
        return 130
    except Exception as ex:
        print(f"[error] {type(ex).__name__}: {ex}")
        return 1
