"""Services and processes: see what's using your PC and take control of it.

Service changes are recorded so they can be undone, and a short list of
services Windows can't run without is protected from being disabled.
"""
from __future__ import annotations

import time
from dataclasses import dataclass

from .util import IS_WINDOWS, Emit, NeedsAdmin, default_emit, is_admin, load_state, run, save_state

try:
    import psutil
except ImportError:  # pragma: no cover
    psutil = None  # type: ignore

# Services whose start type mistri refuses to set to disabled/manual.
PROTECTED_SERVICES = {
    "rpcss", "dcomlaunch", "rpceptmapper", "lsm", "samss", "eventlog", "plugplay", "power", "profsvc",
    "schedule", "winmgmt", "cryptsvc", "bfe", "mpssvc", "windefend", "wscsvc", "securityhealthservice",
    "dhcp", "dnscache", "nsi", "brokerinfrastructure", "systemeventsbroker", "coremessagingregistrar",
    "usermanager", "staterepository", "audiosrv", "audioendpointbuilder", "lanmanworkstation",
    "wuauserv", "trustedinstaller", "gpsvc", "sens", "themes", "tokenbroker", "appinfo",
}

START_TYPES = {"automatic": "auto", "manual": "demand", "disabled": "disabled", "auto": "auto",
               "demand": "demand", "delayed": "delayed-auto"}


def _need_psutil():
    if psutil is None:
        raise RuntimeError("psutil is not installed. Run: pip install psutil")


# ------------------------------------------------------------------ services

@dataclass
class Service:
    name: str
    display: str
    status: str
    start: str
    binpath: str
    description: str

    @property
    def protected(self) -> bool:
        return self.name.lower() in PROTECTED_SERVICES

    @property
    def microsoft(self) -> bool:
        b = self.binpath.lower()
        return "\\windows\\system32\\" in b or "\\windows\\syswow64\\" in b or "\\windows defender" in b


def list_services(text_filter: str = "", hide_microsoft: bool = False) -> list[Service]:
    _need_psutil()
    if not IS_WINDOWS:
        return []
    out: list[Service] = []
    f = text_filter.lower()
    for s in psutil.win_service_iter():
        try:
            d = s.as_dict()
        except Exception:
            continue
        svc = Service(d["name"], d.get("display_name") or "", d.get("status") or "", d.get("start_type") or "",
                      d.get("binpath") or "", d.get("description") or "")
        if hide_microsoft and svc.microsoft:
            continue
        if f and f not in svc.name.lower() and f not in svc.display.lower():
            continue
        out.append(svc)
    return sorted(out, key=lambda s: s.display.lower())


def set_service_start(name: str, start: str, emit: Emit = default_emit) -> int:
    if not is_admin():
        raise NeedsAdmin("Changing a service needs administrator rights.")
    mode = START_TYPES.get(start.lower())
    if not mode:
        raise ValueError("start type must be one of: automatic, manual, disabled, delayed")
    if name.lower() in PROTECTED_SERVICES and mode in ("disabled", "demand"):
        emit(f"Refusing: '{name}' is needed for Windows to run properly.")
        return 1
    _need_psutil()
    current = psutil.win_service_get(name).start_type()
    state = load_state()
    undo = state.setdefault("services", {})
    undo.setdefault(name, current)          # keep the ORIGINAL value, not the last one
    save_state(state)
    rc = run(["sc.exe", "config", name, "start=", mode], emit)
    if rc == 0:
        emit(f"{name}: start type {current} -> {start}. Undo any time with 'Undo' / 'services undo {name}'.")
    return rc


def undo_service(name: str, emit: Emit = default_emit) -> int:
    state = load_state()
    orig = state.get("services", {}).get(name)
    if not orig:
        emit(f"No recorded change for {name}.")
        return 1
    if not is_admin():
        raise NeedsAdmin("Changing a service needs administrator rights.")
    rc = run(["sc.exe", "config", name, "start=", START_TYPES.get(orig, "demand")], emit)
    if rc == 0:
        state["services"].pop(name, None)
        save_state(state)
        emit(f"{name}: restored to {orig}.")
    return rc


def changed_services() -> dict[str, str]:
    return load_state().get("services", {})


# ------------------------------------------------------------------ processes

@dataclass
class Proc:
    pid: int
    name: str
    cpu: float
    mem: int
    user: str
    exe: str


def top_processes(n: int = 40, sort: str = "mem") -> list[Proc]:
    """Top processes by memory or CPU. CPU is sampled over ~0.5s."""
    _need_psutil()
    procs = list(psutil.process_iter(["pid", "name", "username", "exe", "memory_info"]))
    for p in procs:
        try:
            p.cpu_percent(None)
        except Exception:
            pass
    time.sleep(0.5)
    ncpu = psutil.cpu_count() or 1
    out: list[Proc] = []
    for p in procs:
        try:
            cpu = p.cpu_percent(None) / ncpu
            mi = p.info.get("memory_info")
            out.append(Proc(p.pid, p.info.get("name") or "?", cpu, mi.rss if mi else 0,
                            p.info.get("username") or "", p.info.get("exe") or ""))
        except Exception:
            continue
    key = (lambda x: x.cpu) if sort == "cpu" else (lambda x: x.mem)
    return sorted(out, key=key, reverse=True)[:n]


def system_summary() -> str:
    _need_psutil()
    vm = psutil.virtual_memory()
    cpu = psutil.cpu_percent(interval=0.3)
    disk = psutil.disk_usage("C:\\" if IS_WINDOWS else "/")
    boot = time.time() - psutil.boot_time()
    return (f"CPU {cpu:.0f}%  |  RAM {vm.percent:.0f}% of {vm.total / 2**30:.1f} GB  |  "
            f"System drive {disk.percent:.0f}% full ({disk.free / 2**30:.0f} GB free)  |  "
            f"Up {boot / 3600:.1f} h")


CRITICAL_PROCS = {"system", "smss.exe", "csrss.exe", "wininit.exe", "winlogon.exe", "services.exe",
                  "lsass.exe", "svchost.exe", "dwm.exe", "explorer.exe", "fontdrvhost.exe", "registry",
                  "memory compression", "msmpeng.exe"}


def kill(pid: int, emit: Emit = default_emit) -> bool:
    _need_psutil()
    p = psutil.Process(pid)
    name = p.name()
    if name.lower() in CRITICAL_PROCS:
        emit(f"Refusing to end {name}: Windows needs it.")
        return False
    p.terminate()
    try:
        p.wait(3)
    except psutil.TimeoutExpired:
        p.kill()
    emit(f"Ended {name} (PID {pid}).")
    return True


PRIORITIES = ["idle", "below_normal", "normal", "above_normal", "high"]


def set_priority(pid: int, level: str, emit: Emit = default_emit) -> None:
    _need_psutil()
    if not IS_WINDOWS:
        raise RuntimeError("Priority classes are Windows-only.")
    mapping = {"idle": psutil.IDLE_PRIORITY_CLASS, "below_normal": psutil.BELOW_NORMAL_PRIORITY_CLASS,
               "normal": psutil.NORMAL_PRIORITY_CLASS, "above_normal": psutil.ABOVE_NORMAL_PRIORITY_CLASS,
               "high": psutil.HIGH_PRIORITY_CLASS}
    p = psutil.Process(pid)
    p.nice(mapping[level])
    emit(f"{p.name()} (PID {pid}) priority -> {level.replace('_', ' ')}")
