package org.gitav.paindu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.gitav.paindu.MainActivity
import org.gitav.paindu.R
import org.gitav.paindu.plugins.PluginStatus
import org.gitav.paindu.plugins.Plugins

private const val STEPS = 4

/**
 * The first-run walkthrough. Every step is a choice with "not now" as an equal
 * option, the whole thing can be skipped, and nothing asked here is ever asked
 * again: after setup paindu never prompts, nags or notifies.
 */
@Composable
fun SetupScreen(activity: MainActivity) {
    val settings = activity.settings
    var step by rememberSaveable { mutableIntStateOf(0) }
    val finish: () -> Unit = { settings.setupDone.value = true }
    val next: () -> Unit = {
        if (step >= STEPS - 1) finish() else step += 1
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Step ${step + 1} of $STEPS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = finish) { Text("Skip setup") }
        }
        LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                0 -> Welcome()
                1 -> DefaultBrowserStep(activity)
                2 -> ScreenLockStep(activity)
                else -> PluginsStep(activity)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) OutlinedButton(onClick = { step -= 1 }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(onClick = next) {
                Text(
                    when (step) {
                        0 -> "Start"
                        STEPS - 1 -> "Finish"
                        else -> "Next"
                    }
                )
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Welcome() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.paindu_logo), contentDescription = null, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "Welcome to paindu",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text("پینڈو", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
    SectionCard("A simple, careful browser", painterResource(R.drawable.ic_shield)) {
        Text("Shield is already on: it blocks ads, trackers, malware sites and pop-ups, and rejects cookie banners for you.")
        Text("No accounts. No ads. No telemetry. Nothing runs in the background.")
    }
    Muted(
        "The next three steps are choices, and \"not now\" is always fine. " +
            "After this, paindu will never ask again or send you notifications. " +
            "Everything here can be changed later in Settings."
    )
}

@Composable
private fun DefaultBrowserStep(activity: MainActivity) {
    StepTitle("Open links with paindu?")
    SectionCard("Default browser", rememberVectorPainter(Icons.Filled.Star)) {
        Text("If paindu is your default browser, links you tap in messages, email and other apps open here, with Shield on.")
        if (activity.isDefaultBrowser) {
            Text("paindu is your default browser.", color = MaterialTheme.colorScheme.primary)
        } else {
            Muted("Android will ask you to confirm. You can change it back at any time in Android's settings.")
            Button(onClick = { activity.requestDefaultBrowser() }) { Text("Make paindu my default browser") }
        }
    }
    Muted("To keep your current browser, just press Next.")
}

@Composable
private fun ScreenLockStep(activity: MainActivity) {
    val settings = activity.settings
    StepTitle("Lock paindu?")
    SectionCard("Screen lock", rememberVectorPainter(Icons.Filled.Lock), Help.APP_LOCK) {
        Text("paindu can ask for your fingerprint, face, PIN, pattern or password before it opens, the same one that unlocks your phone.")
        when {
            settings.appLock.value -> Text("The lock is on.", color = MaterialTheme.colorScheme.primary)
            activity.canUseScreenLock() ->
                Button(onClick = { activity.setAppLock(true) }) { Text("Use my screen lock") }
            else -> {
                Muted("This phone has no screen lock yet. Set one in Android's settings first, then turn this on in paindu's Settings.")
                OutlinedButton(onClick = { activity.openScreenLockSettings() }) { Text("Open Android security settings") }
            }
        }
    }
    Muted("To leave paindu unlocked, just press Next.")
}

@Composable
private fun PluginsStep(activity: MainActivity) {
    val settings = activity.settings
    val browser = activity.browser
    StepTitle("Add anything?")
    Muted("paindu starts with only the browser and Shield. These extras are off unless you switch them on.")
    for (plugin in Plugins.ALL.filter { it.status == PluginStatus.Available }) {
        if (plugin.id == Plugins.WHUSH && !browser.whushSupported) continue
        SectionCard(plugin.name, painterResource(plugin.icon), plugin.about) {
            SwitchRow(
                label = "Use ${plugin.name}",
                checked = settings.pluginOn(plugin.id),
                onChange = { browser.setPlugin(plugin.id, it) },
                detail = plugin.tagline,
            )
        }
    }
    Muted("A password manager is being built. It will appear under Plugins when it's ready; paindu won't notify you.")
}

/** Shown instead of everything else while paindu is waiting for the phone's screen lock. */
@Composable
fun LockScreen(activity: MainActivity) {
    // Ask straight away, once; the button is there if the prompt is dismissed.
    androidx.compose.runtime.LaunchedEffect(Unit) { activity.unlock() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(32.dp),
    ) {
        Image(painterResource(R.drawable.paindu_logo), contentDescription = null, modifier = Modifier.size(96.dp))
        Text(
            "paindu is locked",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Button(onClick = { activity.unlock() }) { Text("Unlock") }
    }
}
