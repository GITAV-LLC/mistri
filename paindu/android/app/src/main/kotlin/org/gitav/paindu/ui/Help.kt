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
}
