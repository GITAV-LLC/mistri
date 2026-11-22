package org.gitav.paindu.plugins

import org.gitav.paindu.R

/**
 * paindu keeps the browser itself small: tabs, bookmarks, downloads and Shield.
 * Everything else is a plugin the user chooses to switch on.
 *
 * This file is the catalogue of built-in plugins. They ship inside the app, cost
 * nothing while off, and do nothing until switched on. The protocol that will let
 * other people publish plugins is drafted in paindu/PLUGINS.md.
 */
enum class PluginStatus { Available, Planned }

class Plugin(
    val id: String,
    val name: String,
    /** One line: what you get. */
    val tagline: String,
    /** The ⓘ text: what it does, what it keeps, and its limits. */
    val about: String,
    val icon: Int,
    val status: PluginStatus = PluginStatus.Available,
)

object Plugins {
    const val WHUSH = "whush"
    const val HISTORY = "history"
    const val CARE = "care"
    const val PASSWORDS = "passwords"
    const val VPN = "vpn"

    val ALL = listOf(
        Plugin(
            WHUSH, "whush",
            "Private tabs that vanish. Whush: gone.",
            "A whush tab keeps its cookies, sign-ins and site data in a separate compartment from your " +
                "normal tabs. When you close the last whush tab, everything in the compartment is wiped, " +
                "and the next whush tab starts in a brand-new one. whush tabs are never added to " +
                "history, never reopened next time, and can't be screenshotted.\n\n" +
                "What whush doesn't do: it doesn't hide your browsing from your mobile network, Wi-Fi " +
                "owner, employer or the sites themselves. Files you download are kept.",
            R.drawable.ic_whush,
        ),
        Plugin(
            HISTORY, "History",
            "Remember the pages you visit, to find them again.",
            "paindu keeps no history unless this plugin is on. With it on, paindu remembers the last " +
                "500 pages you opened, suggests them as you type in the address bar, and shows where you " +
                "left off on the home screen.\n\n" +
                "History stays on this phone. Switching the plugin off deletes it.",
            R.drawable.ic_history,
        ),
        Plugin(
            CARE, "Device care",
            "Check apps for malware, see who has powerful access, find what's using space.",
            "Device care looks at the phone, not the web: it checks installed apps against a database " +
                "of known malware, lists apps that hold powerful kinds of access, shows where storage " +
                "has gone, and reports on memory, battery and security updates.\n\n" +
                "It only reports. Android doesn't let one app change another, so each finding has a " +
                "button that opens the right Android settings screen.",
            R.drawable.ic_care,
        ),
        Plugin(
            PASSWORDS, "Passwords",
            "An encrypted password vault, unlocked by your screen lock.",
            "Planned. Passwords will be encrypted on this phone, unlocked with your screen lock, and " +
                "included in Android's encrypted backup. Imported passwords will be checked against " +
                "known breaches. There will be no way to export passwords as plain text.",
            R.drawable.ic_key,
            PluginStatus.Planned,
        ),
        Plugin(
            VPN, "Bring your own VPN",
            "Send paindu's traffic through a WireGuard server you choose.",
            "Planned. You will be able to paste a WireGuard configuration from any provider, or from " +
                "your own server, and paindu will route only its own browsing through it. GITAV runs " +
                "no VPN servers and will hold no account for you.",
            R.drawable.ic_vpn,
            PluginStatus.Planned,
        ),
    )

    fun byId(id: String): Plugin? = ALL.firstOrNull { it.id == id }
}
