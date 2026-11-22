package org.gitav.paindu.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import org.gitav.paindu.MainActivity
import org.gitav.paindu.R
import org.gitav.paindu.plugins.Plugin
import org.gitav.paindu.plugins.PluginStatus
import org.gitav.paindu.plugins.Plugins

private const val PROTOCOL_URL = "https://github.com/GITAV-LLC/mistri/blob/main/paindu/PLUGINS.md"

/**
 * Plugins: paindu itself stays small, and this is where the user adds what they want.
 * Nothing here is on until its switch is turned on.
 */
@Composable
fun PluginsScreen(activity: MainActivity, openCare: () -> Unit, openUrl: (String) -> Unit, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    TabbedPage("Plugins", onBack, listOf("Available", "Planned"), tab, onSelect = { tab = it }) {
        if (tab == 0) {
            Muted("paindu starts with only the browser and Shield. Switch on what you want; switch it off any time.")
            for (plugin in Plugins.ALL.filter { it.status == PluginStatus.Available }) {
                PluginCard(activity, plugin, openCare, onBack)
            }
        } else {
            Muted("These are being built. They will arrive as plugins, off until you turn them on.")
            for (plugin in Plugins.ALL.filter { it.status == PluginStatus.Planned }) {
                SectionCard(plugin.name, painterResource(plugin.icon), plugin.about) {
                    Text(plugin.tagline)
                    Muted("Not available yet.")
                }
            }
            SectionCard("Plugins from other people", painterResource(R.drawable.ic_plugin)) {
                Text("paindu will have an open plugin protocol and a marketplace, so anyone can publish a plugin.")
                Muted("The draft protocol is public. Comments are welcome.")
                OutlinedButton(onClick = { openUrl(PROTOCOL_URL) }) { Text("Read the draft protocol") }
            }
        }
    }
}

@Composable
private fun PluginCard(activity: MainActivity, plugin: Plugin, openCare: () -> Unit, toBrowser: () -> Unit) {
    val settings = activity.settings
    val browser = activity.browser
    val on = settings.pluginOn(plugin.id)
    val unsupported = plugin.id == Plugins.WHUSH && !browser.whushSupported

    SectionCard(plugin.name, painterResource(plugin.icon), plugin.about) {
        Text(plugin.tagline)
        if (unsupported) {
            Text(
                "whush needs a newer Android System WebView than this phone has. Update it in the Play Store.",
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            SwitchRow(
                label = "Use ${plugin.name}",
                checked = on,
                onChange = { browser.setPlugin(plugin.id, it) },
                detail = when {
                    !on -> "Off"
                    plugin.id == Plugins.HISTORY -> "On. Switching it off deletes your history."
                    plugin.id == Plugins.WHUSH -> "On. Switching it off closes any whush tabs."
                    else -> "On"
                },
            )
        }
        if (on && plugin.id == Plugins.CARE) {
            Button(onClick = openCare) { Text("Open Device care") }
        }
        if (on && plugin.id == Plugins.WHUSH && !unsupported) {
            Button(onClick = {
                browser.addWhushTab()
                toBrowser()
            }) { Text("Open a whush tab") }
        }
        if (on && plugin.id == Plugins.HISTORY) {
            Muted("%,d pages remembered. Find them under History in the browser menu.".format(settings.history.size))
        }
    }
}
