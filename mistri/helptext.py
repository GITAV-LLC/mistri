"""On-demand explanations shown behind the ⓘ buttons. Plain language, no jargon without a gloss."""

PAGES = {
    "home": ("What mistri does",
             "mistri keeps Windows tidy using tools that are already part of Windows plus a few well-known "
             "open-source projects. It never runs in the background and never changes anything until you press "
             "a button. Every action is written to a log you can open from the sidebar.\n\n"
             "A good routine: Clean and run a Quick scan weekly; run a Health check and app updates monthly."),
    "clean": ("About cleanup",
              "Programs and Windows leave behind temporary files, caches and crash reports. Removing them frees "
              "disk space and can fix glitches caused by a stale cache.\n\n"
              "Press Analyze first: you'll see how much each item holds before anything is deleted. Files that are "
              "in use are skipped, temp files newer than a day are kept, and browser caches are left alone while "
              "that browser is open.\n\n"
              "Expect caches to rebuild: the first visit to some websites will be a little slower afterwards."),
    "security": ("About malware scanning",
                 "Microsoft Defender is Windows' built-in antivirus. It already watches your PC in real time; "
                 "mistri lets you run its scans on demand and see what it found.\n\n"
                 "ClamAV is a free, open-source scanner used widely on mail servers. It's a useful second opinion "
                 "but catches less Windows malware than Defender, so treat its results as a hint to investigate.\n\n"
                 "If another antivirus (such as Malwarebytes Premium) is installed, Windows turns Defender's "
                 "scanner off and Defender scans will report an error. That's expected."),
    "repair": ("About repairs",
               "These are Microsoft's own repair tools, the same ones a technician would run.\n\n"
               "They fix damaged Windows system files, which is what actually causes crashes, failed updates and "
               "odd errors. That's why mistri has no 'registry cleaner': Microsoft doesn't support them, they "
               "don't make PCs measurably faster, and they're a common cause of broken programs."),
    "startup": ("About startup items",
                "Programs often add themselves to Windows startup so they open the moment you sign in. A long list "
                "makes sign-in slow and uses memory all day.\n\n"
                "Turning an item off here works exactly like Task Manager's Startup apps page: the program isn't "
                "removed and you can turn it back on any time. Scheduled tasks are a second place programs use to "
                "launch themselves, and a common hiding spot for adware."),
    "services": ("About services",
                 "Services are programs that run in the background without a window, such as updaters, sync "
                 "helpers and device drivers' companions.\n\n"
                 "Start types: Automatic = starts with Windows. Manual = starts only when something asks for it "
                 "(the safe way to slim things down). Disabled = never starts, which can break the program that "
                 "owns it.\n\n"
                 "Microsoft's services are hidden by default. Services Windows needs are marked protected and "
                 "can't be switched off here. Every change is remembered so you can undo it."),
    "processes": ("About processes",
                  "Every running program is a process. This list shows which ones use the most memory or CPU "
                  "right now.\n\n"
                  "Ending a process closes it immediately, without asking to save, so close programs normally when "
                  "you can. Lowering priority keeps a heavy job (a video export, a game launcher) from slowing "
                  "everything else down; it lasts until that program closes. Essential Windows processes can't be "
                  "ended here."),
}

CLEAN = {
    "user_temp": "Scratch files programs create while they run (your %TEMP% folder). Only files older than a day are "
                 "removed, so anything a program is still using is left alone.",
    "windows_temp": "The same kind of scratch files, created by Windows itself and by installers. Older than a day "
                    "only. Needs admin because the folder belongs to the system.",
    "chrome_cache": "Copies of images, scripts and pages Chrome keeps to load sites faster. Removing them frees space "
                    "and fixes pages that display oddly. Logins, history and bookmarks are not touched. Skipped "
                    "while Chrome is open.",
    "edge_cache": "Edge's page, script and graphics cache. Logins, history and favourites are not touched. Skipped "
                  "while Edge is open.",
    "brave_cache": "Brave's page, script and graphics cache. Logins, history and bookmarks are not touched. Skipped "
                   "while Brave is open.",
    "firefox_cache": "Firefox's page cache. Logins, history and bookmarks are not touched. Skipped while Firefox "
                     "is open.",
    "thumbnails": "Small preview images File Explorer stores for your photos and videos. Explorer rebuilds them "
                  "the next time you open those folders.",
    "crash_dumps": "Memory snapshots saved when a program crashes. Only useful to a developer debugging that crash.",
    "error_reports": "Reports Windows prepared about crashes and hangs, waiting to be sent to Microsoft or already "
                     "archived. Safe to remove. Needs admin.",
    "minidumps": "Small dumps written after a blue-screen crash. Keep them if you're troubleshooting blue screens; "
                 "otherwise they're safe to remove. Off by default.",
    "wu_downloads": "Update packages Windows already downloaded and installed. If an update still needs one, "
                    "Windows downloads it again. Off by default because it briefly stops the Windows Update "
                    "service while cleaning.",
    "recycle_bin": "Permanently deletes everything in the Recycle Bin on all drives. Off by default: check it "
                   "doesn't hold something you want back.",
}

SECURITY = {
    "defender": "Windows' built-in antivirus. When it finds something, it quarantines or removes it according to "
                "your Windows Security settings, and you can review it in Threat history.",
    "clamav": "Open-source scanner (by Cisco Talos). Loading its signatures takes about 30 seconds and roughly 1 GB "
              "of memory, then it lists only infected files. If it's missing, press Install ClamAV: mistri downloads Cisco's signed installer and sets it up.",
    "quick": "Checks the places malware usually hides: running programs, startup locations, Downloads, Desktop, "
             "temp and app-data folders. Usually a few minutes.",
    "full": "Checks every file on the PC (ClamAV checks your user folder). Can take an hour or more; you can keep "
            "working while it runs.",
    "custom": "Checks one folder or drive you choose, for example a USB stick or a download you're unsure about.",
    "quarantine": "When on, files ClamAV flags are moved into mistri's quarantine folder, where they can't run. "
                  "When off, ClamAV only reports them so you can decide. Defender always follows its own settings.",
    "update": "Downloads the latest malware signatures for the engines you selected. Defender also updates itself "
              "through Windows Update; ClamAV only updates when asked.",
    "extension": "mistri Shield is the companion Chrome extension. It blocks ads, trackers, known malware and scam "
                 "sites, closes ad popups and clicks 'reject' on cookie banners. Chrome does the blocking itself, so "
                 "it adds almost no overhead.",
}

REPAIR = {
    "full": "Makes a restore point, then runs DISM (repairs the Windows component store, downloading clean copies "
            "from Windows Update) followed by SFC (uses that store to fix system files). This is the standard fix "
            "for crashes, failed updates and strange errors. Takes 15–40 minutes; restart afterwards if it "
            "reports repairs.",
    "dism_scan": "A read-only check of the Windows component store. It reports whether repairs are needed without "
                 "changing anything. A few minutes.",
    "sfc": "System File Checker on its own: compares Windows files to known-good copies and replaces damaged ones. "
           "5–20 minutes. If it says it couldn't fix something, run Full repair.",
    "component_cleanup": "Removes old versions of Windows components left behind by updates. Frees space (often "
                         "several GB) but means those updates can no longer be uninstalled.",
    "restore_point": "A snapshot of Windows settings, drivers and installed programs (not your documents) you can "
                     "roll back to from Settings > System > Recovery. Windows allows one per 24 hours.",
    "chkdsk": "Scans the system drive's file system for errors while you keep working. It only reports; if it finds "
              "problems it tells you how to fix them (that usually needs a restart).",
    "disk_health": "Reads the health your drives report about themselves: overall status, wear level for SSDs, "
                   "temperature, uncorrected read errors and power-on hours. 'Warning' or rising errors mean it's "
                   "time to back up.",
    "updates_list": "Asks winget (Windows' package manager) which of your installed apps have newer versions. "
                    "Nothing is installed.",
    "updates_apply": "Installs every available winget update. Apps being updated may close. Some installers show "
                     "their own window or ask for permission.",
}

STARTUP_TASKS = ("About scheduled tasks",
                 "Scheduled tasks start programs at sign-in, on a timer or on an event. Only tasks from outside "
                 "Microsoft are shown. Updaters (Google, Adobe, Dell) are normal; unfamiliar names that run from "
                 "AppData or Temp deserve a closer look. Disabling a task is reversible.")

UPDATES = ("Updates are checked against mistri's public GitHub releases. Checking sends an ordinary web request to "
           "GitHub and nothing about your PC. When a new version exists you're asked first; the installer is then "
           "downloaded, and it only runs if Windows confirms it carries a valid GITAV code signature. Turn off the "
           "automatic check below if you prefer to check yourself.")
