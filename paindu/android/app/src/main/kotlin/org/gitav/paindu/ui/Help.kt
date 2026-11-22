package org.gitav.paindu.ui

/** The explanations behind the ⓘ buttons, in the same plain, calm tone as the desktop app. */
object Help {
    const val ADS =
        "Blocks requests to known advertising domains when another site tries to load them. " +
            "Uses EasyList and Peter Lowe's list. Ads that a site serves from its own address, " +
            "such as YouTube's, aren't blocked: only whole domains are, which keeps sites working."
    const val TRACKERS =
        "Blocks requests to known tracking domains when another site tries to load them. " +
            "Uses EasyPrivacy. paindu also refuses third-party cookies."
    const val MALWARE =
        "Stops pages on public lists of malware and scam sites (abuse.ch URLhaus and uBlock " +
            "Origin's Badware list) and shows a warning instead. You can still choose to continue."
    const val POPUPS =
        "A page can open a new tab only when you tap something, and one tap opens at most one tab. " +
            "A pop-up tab that lands on a blocked site is closed."
    const val COOKIES =
        "When a cookie banner appears, paindu presses its \"Reject\" or \"Necessary only\" button " +
            "for you. It knows about 25 common banner systems and many languages. It never presses Accept."
    const val COSMETIC = "Hides the empty boxes that are left behind when an ad is blocked."
    const val PAUSE =
        "Turns off blocking, cookie rejection and the pop-up guard on this one site. " +
            "Use it if a site doesn't work properly."
    const val LISTS =
        "The block lists are public and open source. paindu downloads them at most once a day, " +
            "and only while the app is open. The download is an ordinary file request; nothing " +
            "about you is sent."
    const val SAFE_BROWSING =
        "Google Safe Browsing is built into Android's web engine. It warns about dangerous sites " +
            "by comparing a scrambled, partial form of the address with Google's lists. It works alongside " +
            "paindu's own lists. You can turn it off; paindu's lists keep working."
    const val TEXT_SIZE =
        "Makes text on web pages larger or smaller. This is added on top of the font size you " +
            "chose in your phone's settings, which paindu already follows."
    const val SCAN =
        "paindu takes a fingerprint (SHA-256) of every installed app's package and compares it " +
            "with a public database of known malware from abuse.ch MalwareBazaar. Everything happens " +
            "on your phone. This finds known malware only: a brand-new threat won't be listed yet, " +
            "so keep Google Play Protect switched on too."
    const val ACCESS =
        "Android lets some apps do powerful things. This list shows who has that power on your " +
            "phone. paindu can't change another app's access, and wouldn't without asking: press " +
            "Manage next to an app to open its settings page, where you can remove the access or uninstall it."
    const val STORAGE =
        "Android doesn't allow one app to clear another app's cache or data. paindu shows where " +
            "the space has gone; press Manage next to an app to open its settings page, where you can clear its " +
            "cache, clear its storage or uninstall it."
    const val FOLDER =
        "Pick a folder, such as Downloads, and paindu looks through it for known malware, very " +
            "large files, leftover app installers and exact duplicates. Nothing is deleted unless " +
            "you press Delete on a file and confirm."
    const val HEALTH =
        "A quick look at memory, storage, battery and security updates. Android manages memory " +
            "itself, and closing apps to \"free memory\" makes a phone slower, not faster, because " +
            "those apps have to start from scratch next time. So paindu has no memory booster. " +
            "If the phone feels slow, the usual causes are nearly full storage or one misbehaving " +
            "app, and both are shown here."
    const val RESTORE = "When you open paindu, bring back the tabs you had open last time."
    const val APP_LOCK =
        "paindu shows Android's own unlock prompt, so it accepts whatever unlocks your phone: " +
            "fingerprint, face, PIN, pattern or password. paindu never sees or stores any of them. " +
            "It locks again after it has been out of sight for a minute, and while the lock is on " +
            "paindu is hidden from screenshots and the recent-apps screen. If you remove the phone's " +
            "screen lock, this lock turns itself off."
    const val DEFAULT_BROWSER =
        "The default browser is the one Android uses when you tap a link in another app. paindu " +
            "asks only when you press the button, and Android shows its own question to confirm."
    const val SLEEPING_TABS =
        "An open page can use hundreds of megabytes. paindu puts tabs you aren't looking at to " +
            "sleep, keeping only their address and your place in them, and wakes them when you come " +
            "back. It decides by how much memory the phone has free and how long since you used each " +
            "tab, not by a fixed number: a phone with plenty of memory keeps more tabs awake."
    const val BACKUP =
        "If Android's backup is switched on for your Google account, Android saves paindu's " +
            "settings, bookmarks and list of open tabs to your Google Drive, and history too if that " +
            "plugin is on. paindu only allows this when the backup is end-to-end encrypted with your " +
            "phone's screen lock, so neither Google nor GITAV can read it. Cookies, sign-ins and site " +
            "data are never backed up. On a new phone, Android restores it when you install paindu."
}
