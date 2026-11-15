package org.gitav.mistri.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.gitav.mistri.BoolPref
import org.gitav.mistri.BuildConfig
import org.gitav.mistri.MainActivity
import org.gitav.mistri.SEARCH_ENGINES
import org.gitav.mistri.shield.FilterLists
import kotlin.math.roundToInt

private class Credit(val name: String, val license: String, val role: String, val url: String)

// Every open-source project mistri for Android is built on or draws its data from.
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

@Composable
fun SettingsScreen(activity: MainActivity, openUrl: (String) -> Unit, onBack: () -> Unit) {
    val settings = activity.settings
    val shield = activity.shield
    val browser = activity.browser
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    /** A Shield switch: save it, then rebuild what each tab runs. */
    @Composable
    fun shieldSwitch(label: String, pref: BoolPref, help: String) {
        SwitchRow(label, pref.value, help = help, onChange = {
            pref.value = it
            browser.applySettings()
        })
    }

    Page("Settings", onBack) {
        Section("Shield") {
            shieldSwitch("Block ads", settings.ads, Help.ADS)
            shieldSwitch("Block trackers", settings.trackers, Help.TRACKERS)
            shieldSwitch("Block malware and scam sites", settings.malware, Help.MALWARE)
            shieldSwitch("Pop-up guard", settings.popups, Help.POPUPS)
            shieldSwitch("Reject optional cookies", settings.cookies, Help.COOKIES)
            shieldSwitch("Hide empty ad boxes", settings.cosmetic, Help.COSMETIC)
            shieldSwitch("Google Safe Browsing", settings.safeBrowsing, Help.SAFE_BROWSING)
        }

        Section("Block lists", Help.LISTS) {
            for (list in FilterLists.LISTS) {
                val stat = shield.stats[list.id]
                val state = when {
                    stat == null -> "not downloaded yet"
                    stat.ok -> "%,d domains".format(stat.count)
                    else -> "last update failed, using the previous copy (%,d domains)".format(stat.count)
                }
                Text(list.name, style = MaterialTheme.typography.bodyLarge)
                Muted("$state · ${list.license}")
            }
            Muted(
                if (shield.lastUpdate <= 0L) "Using the small built-in starter lists until the first update."
                else "Updated " + DateUtils.getRelativeTimeSpanString(
                    shield.lastUpdate, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                )
            )
            OutlinedButton(enabled = !shield.updating, onClick = { scope.launch { shield.update() } }) {
                Text(if (shield.updating) "Updating…" else "Update now")
            }
            SwitchRow(
                "Update once a day",
                settings.autoUpdate.value,
                onChange = { settings.autoUpdate.value = it },
                detail = "Only while mistri is open",
            )
        }

        if (settings.paused.isNotEmpty()) {
            Section("Paused sites", Help.PAUSE) {
                for (host in settings.paused.sorted()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(host, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = {
                            settings.togglePaused(host)
                            browser.applySettings()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Turn protection back on for $host")
                        }
                    }
                }
            }
        }

        Section("Browsing") {
            Text("Search with", style = MaterialTheme.typography.titleSmall)
            for (engine in SEARCH_ENGINES) {
                val selected = settings.searchEngine == engine.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            settings.chooseSearchEngine(engine.id)
                        },
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(engine.name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            SwitchRow(
                "Reopen tabs from last time",
                settings.restoreTabs.value,
                onChange = { settings.restoreTabs.value = it },
                help = Help.RESTORE,
            )
        }

        Section("Reading", Help.TEXT_SIZE) {
            var scale by remember { mutableFloatStateOf(settings.textScale.toFloat()) }
            Text("Page text size: ${scale.roundToInt()}%")
            Slider(
                value = scale,
                onValueChange = { scale = it },
                valueRange = 75f..200f,
                steps = 4,
                onValueChangeFinished = {
                    settings.chooseTextScale(scale.roundToInt())
                    browser.applySettings()
                },
                modifier = Modifier.semantics { contentDescription = "Page text size" },
            )
            Muted(
                "Reader view and Read aloud are in the browser menu. mistri follows your phone's font " +
                    "size, dark theme and screen reader settings automatically."
            )
        }

        Section("Your data") {
            Muted("Cookies, site data, cache and history are stored only on this phone.")
            OutlinedButton(onClick = { confirmClear = true }) { Text("Clear browsing data") }
            LinkRow("Privacy policy") {
                openUrl("https://github.com/GITAV-LLC/mistri/blob/main/PRIVACY.md")
            }
        }

        Section("About") {
            Text("mistri for Android ${BuildConfig.VERSION_NAME}")
            Muted(
                "By GITAV LLC. Free and open source under the MIT licence. No accounts, no ads, " +
                    "no telemetry. mistri is built on the work of these open-source projects:"
            )
            for (credit in CREDITS) {
                LinkRow(credit.name, "${credit.role} · ${credit.license}") { openUrl(credit.url) }
            }
            LinkRow("Source code") { openUrl("https://github.com/GITAV-LLC/mistri") }
        }
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
                    browser.clearBrowsingData()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
