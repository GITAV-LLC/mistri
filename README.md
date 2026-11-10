# mistri by GITAV

**mistri** (مستری) is the everyday word for a repairman or mechanic, and this is one for your PC. It's a small, transparent Windows maintenance toolkit that does the useful parts of CCleaner and Malwarebytes with Windows' own repair tools and open-source projects. It has no background services, telemetry or upsells, and nothing runs unless you press a button (or schedule it yourself).

## Install

1. Download the latest **`mistri-setup-<version>.exe`** (or the `.zip` if your browser blocks .exe files) from [Releases](https://github.com/GITAV-LLC/mistri/releases) and run it and follow the steps. It installs to `C:\Program Files\mistri` with its own private Python runtime, so nothing else on the PC is touched and nothing needs installing first.
   - Releases are code-signed by GITAV, so Windows shows GITAV as the verified publisher.
   - Untick **ClamAV scanner** for a quick install. You can add it later from the Security page.
2. Open **mistri** from the Start menu. It asks for administrator rights so every tool works.
3. For the Chrome extension, open **Security → Set up the Chrome extension…**. It opens `chrome://extensions` and the extension folder: switch on **Developer mode**, click **Load unpacked**, and pick that folder.

Requirements: 64-bit Windows 10 or 11; Chrome 121 or newer for the extension.

Uninstall from **Settings → Apps → mistri**. It removes the program, shortcuts and any scheduled clean-up, and asks whether to delete your settings and logs too. ClamAV, if you installed it, stays until you remove it separately.

Silent install for IT: `mistri-setup-<version>.exe /S` (add `/D=C:\path` to change the folder; it must come last). Silent uninstall: `"C:\Program Files\mistri\uninstall.exe" /S`.

**Updates:** mistri checks GitHub for a new release once a day (turn this off on Home → About mistri) and asks before installing. It runs an update only if Windows confirms the installer is signed by GITAV.

## What it does

| Tab | What happens | Under the hood |
|---|---|---|
| **Clean** | Shows how much each junk category holds, then deletes only what you ticked | Temp folders (files older than 24 h), Chrome/Edge/Brave/Firefox caches, thumbnail cache, crash dumps, error reports, optional Windows Update downloads and Recycle Bin |
| **Security** | Quick, full or folder scans; signature updates; Defender threat history; quarantine | Microsoft Defender (`MpCmdRun.exe`) as the main engine, ClamAV (`clamscan`/`freshclam`) as a second opinion |
| **Repair** | Fixes real Windows corruption, checks disks, updates apps | `DISM /RestoreHealth`, `sfc /scannow`, `DISM /StartComponentCleanup`, `chkdsk /scan`, storage reliability counters, `winget upgrade` |
| **Startup** | Turn startup apps and third-party scheduled tasks on or off | The same `StartupApproved` registry switches Task Manager uses; `Enable/Disable-ScheduledTask` |
| **Services** | Change a service's start type, with undo | `sc config`; original values saved to `%LOCALAPPDATA%\mistri\state.json` |
| **Processes** | Top CPU and memory users; end a process or lower its priority | `psutil` |

### Safety rules built into the engine

- Cleanup never follows symlinks or junctions, skips files in use, and skips a browser's cache while that browser is open.
- Nothing is deleted without an Analyze step first (the CLI asks unless you pass `--yes`).
- **Full repair** creates a System Restore point before it starts.
- Startup changes are reversible toggles; nothing is deleted from the registry.
- About 35 core services (RPC, Defender, Windows Update, networking, audio…) can't be disabled, and essential processes such as `lsass.exe` or `explorer.exe` can't be ended.
- ClamAV only reports by default; quarantine is opt-in and goes to `%LOCALAPPDATA%\mistri\quarantine`.
- Every action is logged to `%LOCALAPPDATA%\mistri\logs`.

### Why there's no registry cleaner

Microsoft does not support registry cleaners. Leftover registry keys take up a few kilobytes and don't slow Windows down, while deleting the wrong one breaks apps. The problems people hope a registry cleaner will fix are better handled by DISM + SFC (corruption), the Startup tab (slow boot) and `winget` (outdated software).

## Command line

Every action is also available from Command Prompt through `mistri.cmd` in the install folder (`"C:\Program Files\mistri\mistri.cmd" clean --preview`, or add that folder to your PATH and type `mistri …`):

```text
mistri                               open the GUI
mistri clean --list                  show cleanup targets
mistri clean --preview               what would be removed (default targets)
mistri clean --targets user_temp,chrome_cache -y
mistri scan                          quick scan with Defender + ClamAV
mistri scan --engine clamav --path D:\Downloads --quarantine
mistri scan --full
mistri update-defs
mistri threats                       Defender detection history
mistri repair full                   restore point → DISM → SFC
mistri repair disk-health
mistri setup-clamav                   download, verify and install ClamAV
mistri updates [--apply]             winget app updates
mistri startup list | disable 3 | enable 3
mistri tasks list | disable 2
mistri services list --third-party
mistri services set SomeService manual
mistri services undo SomeService
mistri procs top --sort cpu -n 15
mistri procs priority 1234 below_normal
mistri schedule weekly --day SUN --time 03:00 [--elevated]
mistri schedule remove
```

Exit codes: 0 OK, 5 needs administrator, other non-zero = the tool reported a problem.

## mistri Shield (Chrome extension)

| Feature | How |
|---|---|
| Ads | EasyList + Peter Lowe's list, blocked as third-party requests |
| Trackers | EasyPrivacy, blocked as third-party requests |
| Malware and scam sites | abuse.ch URLhaus + uBlock "Badware risks". Pages show a warning with a "go back" button and a per-session override |
| Popups | Closes popup tabs that land on a blocked domain, allows one `window.open` per click, and blocks notification prompts that weren't triggered by a click |
| Cookies | Clicks "Reject all / Necessary only" on about 25 common consent platforms (OneTrust, Cookiebot, Didomi, Usercentrics, Sourcepoint, Quantcast-style text buttons, and more), plus a multilingual text match. It never clicks "Accept" |
| Empty ad boxes | A short, conservative CSS list |

It uses Chrome's `declarativeNetRequest`, so Chrome itself does the blocking and the extension never sees your traffic. The lists update every 24 hours (or press **Update lists**). The current lists come to roughly 95,000 domains packed into about 50 rules, well under Chrome's 30,000-rule limit. A small built-in starter set covers you before the first download finishes. **Pause on this site** in the toolbar popup turns everything off for a site that misbehaves.

Limits worth knowing: the extension blocks whole domains only, not the fine-grained path and cosmetic rules full blockers use, so some ads served from a site's own domain (YouTube, for example) will still show. For maximum blocking, run uBlock Origin Lite alongside it and switch off Shield's Ads/Trackers toggles. Cookie auto-reject works on most banners, but sites with unusual banners may still need one click from you.

## Honest limits

- mistri is an on-demand toolkit. Real-time protection still comes from Microsoft Defender, which stays on. If another real-time antivirus (such as Malwarebytes Premium) is registered, Windows turns Defender's antivirus off and its on-demand scans fail with an error in the output. Use ClamAV or that product's scanner instead.
- ClamAV's detection rate on Windows malware is well below Defender's. Treat it as a second opinion, not a replacement.
- Windows only allows one restore point every 24 hours by default. If one already exists, Windows reuses it.

## Open-source components

| Component | License |
|---|---|
| [ClamAV](https://github.com/Cisco-Talos/clamav) | GPL-2.0 |
| [psutil](https://github.com/giampaolo/psutil) | BSD-3-Clause |
| [EasyList / EasyPrivacy](https://easylist.to) | GPLv3 / CC BY-SA 3.0 |
| [uBlock Origin filters (uAssets)](https://github.com/uBlockOrigin/uAssets) | GPLv3 |
| [URLhaus](https://urlhaus.abuse.ch) | CC0 |
| [Peter Lowe's list](https://pgl.yoyo.org/adservers/) | Free to use; see site |

Microsoft Defender, SFC, DISM, CHKDSK and winget are built into Windows.

## Building the installer

Everything builds on Linux or macOS, so no Windows machine is needed:

```bash
sudo apt install nsis            # makensis
pip install uv                   # fetches the Windows Python runtime
./installer/build.sh             # -> dist/mistri-setup-<version>.exe
python branding/make_icons.py    # only if the logo or icon changes
```

The build bundles a standalone Windows CPython 3.12 (python-build-standalone, with Tk), trims it to about 50 MB, adds psutil's Windows wheel, precompiles everything and packs it with NSIS (about 12 MB download). To run from source on Windows instead: `pip install psutil`, then `run.bat` or `py -m mistri`.

**Releases** are built, signed and published automatically by GitHub Actions when a version tag is pushed; see [SIGNING.md](SIGNING.md). To sign a local build by hand instead: sign `mistri-setup-*.exe` with a code-signing certificate (`signtool sign /fd sha256 /tr http://timestamp.digicert.com /td sha256 /f gitav.pfx dist\mistri-setup-*.exe`). This removes the SmartScreen warning once the certificate builds reputation.

## License

MIT, see [LICENSE](LICENSE). Third-party components keep their own licenses; see `installer/THIRD-PARTY-NOTICES.txt`.
