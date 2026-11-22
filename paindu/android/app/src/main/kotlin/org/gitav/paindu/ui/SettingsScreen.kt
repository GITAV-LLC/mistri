package org.gitav.paindu.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gitav.paindu.BoolPref
import org.gitav.paindu.BuildConfig
import org.gitav.paindu.MainActivity
import org.gitav.paindu.R
import org.gitav.paindu.SEARCH_ENGINES
import org.gitav.paindu.shield.FilterLists
import kotlin.math.roundToInt

private class Credit(val name: String, val license: String, val role: String, val url: String)

// Every open-source project paindu is built on or draws its data from.
private val CREDITS = listOf(
    Credit("Chromium / Android System WebView", "BSD 3-Clause", "Renders web pages", "https://www.chromium.org/"),
    Credit("Jetpack Compose, Material 3 and AndroidX", "Apache 2.0", "The interface and accessibility support", "https://developer.android.com/jetpack"),
    Credit("Kotlin and kotlinx.coroutines", "Apache 2.0", "Programming language", "https://kotlinlang.org/"),
    Credit("Mozilla Readability", "Apache 2.0", "Reader view and read aloud", "https://github.com/mozilla/readability"),
    Credit("EasyList and EasyPrivacy", "GPLv3 / CC BY-SA 3.0", "Ad and tracker lists", "https://easylist.to/"),
    Credit("Peter Lowe's Blocklist", "Free to use", "Ad server list", "https://pgl.yoyo.org/adservers/"),
    Credit("abuse.ch URLhaus", "CC0", "Malware site list", "https://urlhaus.abuse.ch/"),
    Credit("uBlock Origin uAssets", "GPLv3", "Badware risks list", "https://github.com/uBlockOrigin/uAssets"),
    Credit("abuse.ch MalwareBazaar", "CC0", "Malware file signatures", "https://bazaar.abuse.ch/"),
    Credit("Material Symbols", "Apache 2.0", "Icons", "https://fonts.google.com/icons"),
    Credit("Hypatia (Divested Computing)", "AGPLv3, design reference only", "Showed that hash-based scanning works on a phone", "https://github.com/Divested-Mobile/Hypatia"),
)

private const val PRIVACY_URL = "https://github.com/GITAV-LLC/mistri/blob/main/paindu/PRIVACY.md"
private const val SOURCE_URL = "https://github.com/GITAV-LLC/mistri/tree/main/paindu"

private val TABS = listOf("Shield", "Browsing", "Reading", "Privacy", "About")

/** Settings, one topic per tab. Each tab is a short list of clearly separated sections. */
@Composable
fun SettingsScreen(activity: MainActivity, openUrl: (String) -> Unit, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    TabbedPage("Settings", onBack, TABS, tab, onSelect = { tab = it }) {
        when (tab) {
            0 -> ShieldTab(activity)
            1 -> BrowsingTab(activity)
            2 -> ReadingTab(activity)
            3 -> PrivacyTab(activity, openUrl)
            else -> AboutTab(activity, openUrl)
        }
    }
}

// ---------------------------------------------------------------- Shield

@Composable
private fun ShieldTab(activity: MainActivity) {
    val settings = activity.settings
    val shield = activity.shield
    val browser = activity.browser
    val scope = rememberCoroutineScope()

    /** A Shield switch: save it, then rebuild what each tab runs. */
    @Composable
    fun shieldSwitch(label: String, pref: BoolPref, help: String) {
        SwitchRow(label, pref.value, help = help, onChange = {
            pref.value = it
            browser.applySettings()
        })
    }

    SectionCard("Protection", painterResource(R.drawable.ic_shield)) {
        shieldSwitch("Block ads", settings.ads, Help.ADS)
        RowDivider()
        shieldSwitch("Block trackers", settings.trackers, Help.TRACKERS)
        RowDivider()
        shieldSwitch("Block malware and scam sites", settings.malware, Help.MALWARE)
        RowDivider()
        shieldSwitch("Pop-up guard", settings.popups, Help.POPUPS)
        RowDivider()
        shieldSwitch("Reject optional cookies", settings.cookies, Help.COOKIES)
        RowDivider()
        shieldSwitch("Hide empty ad boxes", settings.cosmetic, Help.COSMETIC)
    }

    SectionCard("Google Safe Browsing", rememberVectorPainter(Icons.Filled.Lock), Help.SAFE_BROWSING) {
        SwitchRow(
            "Warn about dangerous sites",
            settings.safeBrowsing.value,
            detail = "Built into Android's web engine; works alongside paindu's own lists",
            onChange = {
                settings.safeBrowsing.value = it
                browser.applySettings()
            },
        )
    }

    SectionCard("Block lists", rememberVectorPainter(Icons.AutoMirrored.Filled.List), Help.LISTS) {
        for (list in FilterLists.LISTS) {
            val stat = shield.stats[list.id]
            val state = when {
                stat == null -> "not downloaded yet"
                stat.ok -> "%,d domains".format(stat.count)
                else -> "last update failed, using the previous copy (%,d domains)".format(stat.count)
            }
            Column {
                Text(list.name, style = MaterialTheme.typography.bodyLarge)
                Muted("$state · ${list.license}")
            }
        }
        RowDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Muted(
                    if (shield.lastUpdate <= 0L) "Using the small built-in starter lists until the first update."
                    else "Updated " + DateUtils.getRelativeTimeSpanString(
                        shield.lastUpdate, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    )
                )
            }
            Button(enabled = !shield.updating, onClick = { scope.launch { shield.update() } }) {
                Text(if (shield.updating) "Updating…" else "Update now")
            }
        }
        RowDivider()
        SwitchRow(
            "Update once a day",
            settings.autoUpdate.value,
            onChange = { settings.autoUpdate.value = it },
            detail = "Only while paindu is open",
        )
    }

    SectionCard("Paused sites", rememberVectorPainter(Icons.Filled.Place), Help.PAUSE) {
        if (settings.paused.isEmpty()) {
            Muted("Protection is on everywhere. To pause it on one site, press the shield next to the address bar.")
        }
        for (host in settings.paused.sorted()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(host, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(
                    onClick = {
                        settings.togglePaused(host)
                        browser.applySettings()
                    },
                    modifier = Modifier.semantics { contentDescription = "Protect again: $host" },
                ) { Text("Protect again") }
            }
        }
    }
}

// ---------------------------------------------------------------- Browsing

@Composable
private fun BrowsingTab(activity: MainActivity) {
    val settings = activity.settings

    SectionCard("Default browser", rememberVectorPainter(Icons.Filled.Star), Help.DEFAULT_BROWSER) {
        if (activity.isDefaultBrowser) {
            Text("paindu is your default browser. Links from other apps open here.")
            OutlinedButton(onClick = { activity.openDefaultAppsSettings() }) { Text("Change in Android settings") }
        } else {
            Text("Another browser opens the links you tap in other apps.")
            Button(onClick = { activity.requestDefaultBrowser() }) { Text("Make paindu my default browser") }
        }
    }

    SectionCard("Search engine", rememberVectorPainter(Icons.Filled.Search)) {
        Muted("Used when you type words instead of a web address.")
        for (engine in SEARCH_ENGINES) {
            OptionRow(engine.name, settings.searchEngine == engine.id) { settings.chooseSearchEngine(engine.id) }
        }
    }

    SectionCard("Tabs", painterResource(R.drawable.ic_tabs)) {
        SwitchRow(
            "Reopen tabs from last time",
            settings.restoreTabs.value,
            onChange = { settings.restoreTabs.value = it },
            help = Help.RESTORE,
        )
    }

    SectionCard("Sleeping tabs", painterResource(R.drawable.ic_sleep), Help.SLEEPING_TABS) {
        val tabs = activity.browser.tabs
        val asleep = tabs.count { it.asleep }
        Text("${tabs.size} open, $asleep asleep to save memory.")
        Muted("Tabs you aren't using are put to sleep when the phone runs short of memory, and wake when you return to them.")
    }
}

// ---------------------------------------------------------------- Reading

@Composable
private fun ReadingTab(activity: MainActivity) {
    val settings = activity.settings
    val browser = activity.browser

    SectionCard("Page text size", painterResource(R.drawable.ic_text_size), Help.TEXT_SIZE) {
        var scale by remember { mutableFloatStateOf(settings.textScale.toFloat()) }
        fun setScale(value: Float) {
            scale = value.coerceIn(75f, 200f)
            settings.chooseTextScale(scale.roundToInt())
            browser.applySettings()
        }
        Text("${scale.roundToInt()}%", style = MaterialTheme.typography.headlineSmall)
        Slider(
            value = scale,
            onValueChange = { scale = it },
            valueRange = 75f..200f,
            steps = 4,
            onValueChangeFinished = { setScale(scale) },
            modifier = Modifier.semantics { contentDescription = "Page text size" },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = scale > 75f, onClick = { setScale(scale - 25f) }) { Text("Smaller") }
            OutlinedButton(
                enabled = scale < 200f,
                onClick = { setScale(scale + 25f) },
                modifier = Modifier.padding(start = 12.dp),
            ) { Text("Larger") }
            TextButton(
                enabled = scale.roundToInt() != 100,
                onClick = { setScale(100f) },
                modifier = Modifier.padding(start = 12.dp),
            ) { Text("Reset") }
        }
    }

    SectionCard("Reader view and read aloud", rememberVectorPainter(Icons.Filled.PlayArrow)) {
        Text("Reader view strips a page down to its article, in a clean, large typeface.")
        Text("Read aloud speaks the article using your phone's own text-to-speech voice.")
        Muted("Both are in the browser's menu (the three dots next to the address bar).")
    }

    SectionCard("Follows your phone", rememberVectorPainter(Icons.Filled.Settings)) {
        Text("paindu uses the settings you have already chosen in Android:")
        Muted("• Font size and display size\n• Dark theme, for the app and for web pages\n• TalkBack and other screen readers\n• Switch access and keyboard navigation")
    }
}

// ---------------------------------------------------------------- Privacy

@Composable
private fun PrivacyTab(activity: MainActivity, openUrl: (String) -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }

    SectionCard("What paindu never does", rememberVectorPainter(Icons.Filled.CheckCircle)) {
        Text("No accounts. No ads. No telemetry. Nothing runs in the background.")
        Muted(
            "Your history, bookmarks, cookies and the results of Device care stay on this phone. " +
                "GITAV has no servers that receive anything from paindu."
        )
    }

    SectionCard("Screen lock", rememberVectorPainter(Icons.Filled.Lock), Help.APP_LOCK) {
        val lockOn = activity.settings.appLock.value
        if (lockOn || activity.canUseScreenLock()) {
            SwitchRow(
                "Ask for my screen lock to open paindu",
                lockOn,
                onChange = { activity.setAppLock(it) },
                detail = "Fingerprint, face, PIN, pattern or password",
            )
        } else {
            Text("This phone has no screen lock yet. Set one in Android's settings, then come back.")
            OutlinedButton(onClick = { activity.openScreenLockSettings() }) { Text("Open Android security settings") }
        }
    }

    SectionCard("Backup to Google Drive", painterResource(R.drawable.ic_storage), Help.BACKUP) {
        Text("Settings, bookmarks and open tabs are included in Android's own encrypted backup, if you have it switched on.")
        Muted("Never included: cookies, sign-ins, site data, and anything from whush tabs.")
    }

    SectionCard("Browsing data", rememberVectorPainter(Icons.Filled.Delete)) {
        Muted("Cookies, site data, the cache and your history. Bookmarks and settings are kept.")
        OutlinedButton(
            onClick = { confirmClear = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Clear browsing data") }
    }

    SectionCard("Privacy policy", rememberVectorPainter(Icons.Filled.Lock)) {
        Muted("The full policy, including every network request paindu makes and why.")
        Button(onClick = { openUrl(PRIVACY_URL) }) { Text("Read the privacy policy") }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing data?") },
            text = {
                Text(
                    "This removes cookies, site data, the cache and your history. You'll be signed " +
                        "out of websites. Bookmarks and settings are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    activity.browser.clearBrowsingData()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- About

@Composable
private fun AboutTab(activity: MainActivity, openUrl: (String) -> Unit) {
    SectionCard("About paindu", rememberVectorPainter(Icons.Filled.Info)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.paindu_logo), contentDescription = null, modifier = Modifier.size(64.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text("paindu  ·  پینڈو", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Muted("by GITAV · version ${BuildConfig.VERSION_NAME}")
            }
        }
        Text(
            "A paindu is someone from the village. GITAV stands for Gahandaran – It Takes A Village. " +
                "PB is Punjab, and it is also paindu browser."
        )
        Muted("Free and open source under the MIT licence.")
        Button(onClick = { openUrl(SOURCE_URL) }) { Text("View the source code") }
        OutlinedButton(onClick = { activity.settings.setupDone.value = false }) { Text("Run the setup walkthrough again") }
    }

    SectionCard("Open-source credits", rememberVectorPainter(Icons.Filled.Favorite)) {
        Muted("paindu is built on the work of these projects.")
        for ((index, credit) in CREDITS.withIndex()) {
            if (index > 0) RowDivider()
            ActionRow(credit.name, "${credit.role} · ${credit.license}", "Website") { openUrl(credit.url) }
        }
    }
}
