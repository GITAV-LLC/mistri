package org.gitav.mistri.ui

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gitav.mistri.care.AccessGroup
import org.gitav.mistri.care.AppItem
import org.gitav.mistri.care.AppSize
import org.gitav.mistri.care.Care
import org.gitav.mistri.care.FolderReport
import org.gitav.mistri.care.FoundFile
import org.gitav.mistri.care.Health
import org.gitav.mistri.care.Signatures

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Device care. Every card here reports; none of them changes anything on its own.
 * Where something can be done, a row opens the right Android settings screen.
 */
@Composable
fun CareScreen(signatures: Signatures, resumeTick: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }

    var health by remember { mutableStateOf<Health?>(null) }
    var access by remember { mutableStateOf<List<AccessGroup>?>(null) }
    var usageAccess by remember { mutableStateOf(false) }
    var sizes by remember { mutableStateOf<List<AppSize>>(emptyList()) }

    // Re-read everything when the screen opens, when Refresh is pressed, and when the
    // user comes back from a system settings screen (where they may have changed something).
    LaunchedEffect(resumeTick, refresh) {
        withContext(Dispatchers.IO) { signatures.get() }
        health = withContext(Dispatchers.IO) { Care.health(context) }
        usageAccess = Care.hasUsageAccess(context)
        access = withContext(Dispatchers.IO) { Care.powerfulAccess(context) }
        sizes = if (usageAccess) withContext(Dispatchers.IO) { Care.appSizes(context) } else emptyList()
    }

    Page(
        title = "Device care",
        onBack = onBack,
        actions = {
            IconButton(onClick = { refresh += 1 }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Check again")
            }
        },
    ) {
        MalwareCard(signatures)
        AccessCard(access)
        StorageCard(health, usageAccess, sizes)
        FolderCard(signatures)
        HealthCard(health)
    }
}

// ---------------------------------------------------------------- malware

@Composable
private fun MalwareCard(signatures: Signatures) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var hits by remember { mutableStateOf<List<AppItem>?>(null) }
    var scanned by remember { mutableIntStateOf(0) }
    var updating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Section("Check apps for malware", Help.SCAN) {
        val count = signatures.count
        Muted(
            if (count < 0) "No signature database on this phone yet."
            else "%,d known-malware signatures, updated %s.".format(count, ago(signatures.updatedAt))
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

        if (busy) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Muted("Checking installed apps…")
        }

        hits?.let { found ->
            if (found.isEmpty()) {
                Text(
                    "Checked $scanned apps. None match known malware.",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                Text(
                    "${found.size} of $scanned apps match known malware. Open each one to uninstall it.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                for (app in found) LinkRow(app.label, app.detail) { Care.openApp(context, app.pkg) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !busy && !updating && count >= 0,
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val db = signatures.get() ?: return@withContext null
                            Care.scanApps(context, db) { done, total ->
                                progress = if (total == 0) 1f else done.toFloat() / total
                                scanned = total
                            }
                        }
                        hits = result
                        busy = false
                    }
                },
            ) { Text("Check apps") }
            OutlinedButton(
                enabled = !busy && !updating,
                modifier = Modifier.padding(start = 12.dp),
                onClick = {
                    updating = true
                    message = null
                    scope.launch {
                        message = signatures.update()
                        updating = false
                    }
                },
            ) { Text(if (updating) "Downloading…" else if (count < 0) "Get signatures" else "Update signatures") }
        }
        LinkRow("Google Play Protect", "Android's built-in app scanner. Check that it's switched on.") {
            Care.openPlayProtect(context)
        }
    }
}

// ---------------------------------------------------------------- powerful access

@Composable
private fun AccessCard(access: List<AccessGroup>?) {
    val context = LocalContext.current
    Section("Apps with powerful access", Help.ACCESS) {
        if (access == null) {
            Muted("Looking…")
            return@Section
        }
        val groups = access.filter { it.apps.isNotEmpty() }
        if (groups.isEmpty()) {
            Text("No apps hold any of the powerful kinds of access mistri looks for.")
        }
        for ((index, group) in groups.withIndex()) {
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${group.title} (${group.apps.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                InfoButton(group.title, group.help)
            }
            for (app in group.apps) LinkRow(app.label) { Care.openApp(context, app.pkg) }
        }
    }
}

// ---------------------------------------------------------------- storage

@Composable
private fun StorageCard(health: Health?, usageAccess: Boolean, sizes: List<AppSize>) {
    val context = LocalContext.current
    fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    Section("Storage", Help.STORAGE) {
        if (health != null) {
            val used = health.storageTotal - health.storageFree
            val fraction = if (health.storageTotal > 0) used.toFloat() / health.storageTotal else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text("${size(health.storageFree)} free of ${size(health.storageTotal)}")
            if (fraction > 0.9f) {
                Text(
                    "Storage is nearly full. Phones slow down noticeably when this happens.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        LinkRow("Free up space", "Opens Android's own storage manager") { Care.openStorage(context) }

        if (!usageAccess) {
            Muted(
                "To show how much space each app takes and when you last used it, Android needs you " +
                    "to give mistri \"usage access\". It's optional, and the information stays on this phone."
            )
            OutlinedButton(onClick = { Care.openUsageAccess(context) }) { Text("Open usage access settings") }
        } else {
            val now = System.currentTimeMillis()
            if (sizes.isNotEmpty()) {
                Text("Largest apps", style = MaterialTheme.typography.titleSmall)
                for (app in sizes.take(8)) {
                    LinkRow(app.label, "${size(app.total)} in total, ${size(app.cache)} of it cache") {
                        Care.openApp(context, app.pkg)
                    }
                }
            }
            val unused = sizes.filter { it.lastUsed in 1 until (now - 90 * DAY_MS) }.take(8)
            if (unused.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Not opened in three months", style = MaterialTheme.typography.titleSmall)
                for (app in unused) {
                    LinkRow(app.label, "${size(app.total)}, last used ${ago(app.lastUsed)}") {
                        Care.openApp(context, app.pkg)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- folder check

@Composable
private fun FolderCard(signatures: Signatures) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var seen by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<FolderReport?>(null) }
    val deleted = remember { mutableStateListOf<Uri>() }
    var toDelete by remember { mutableStateOf<FoundFile?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }

    fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            busy = true
            seen = 0
            report = null
            deleted.clear()
            scope.launch {
                report = withContext(Dispatchers.IO) {
                    Care.scanFolder(context, uri, signatures.get()) { seen = it }
                }
                busy = false
            }
        }
    }

    @Composable
    fun fileRow(file: FoundFile, note: String) {
        if (file.uri in deleted) return
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge)
                Muted("${file.path}  ${size(file.size)}  $note".trim())
            }
            TextButton(onClick = { toDelete = file }) { Text("Delete") }
        }
    }

    Section("Check a folder", Help.FOLDER) {
        Button(enabled = !busy, onClick = { picker.launch(null) }) { Text("Choose a folder") }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Muted("Looking through the folder… $seen files so far")
        }
        failed?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        report?.let { r ->
            Text(
                buildString {
                    append("Looked at ${r.scanned} files.")
                    if (r.truncated) append(" The folder has more; mistri stopped at ${r.scanned}.")
                    if (!r.checkedForMalware) append(" They weren't checked for malware: no signature database yet.")
                    else if (r.malware.isEmpty()) append(" None match known malware.")
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (r.malware.isNotEmpty()) {
                Text(
                    "Matches known malware",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                for (f in r.malware) fileRow(f, "")
            }
            if (r.installers.isNotEmpty()) {
                Text("Leftover app installers", style = MaterialTheme.typography.titleSmall)
                Muted("Once an app is installed, its installer file is no longer needed.")
                for (f in r.installers) fileRow(f, "")
            }
            if (r.large.isNotEmpty()) {
                Text("Very large files", style = MaterialTheme.typography.titleSmall)
                for (f in r.large) fileRow(f, "")
            }
            if (r.duplicates.isNotEmpty()) {
                Text("Exact duplicates", style = MaterialTheme.typography.titleSmall)
                Muted("Each group below is the same file saved more than once. Keep one copy.")
                for ((index, group) in r.duplicates.withIndex()) {
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    for (f in group) fileRow(f, "")
                }
            }
            if (r.malware.isEmpty() && r.installers.isEmpty() && r.large.isEmpty() && r.duplicates.isEmpty()) {
                Muted("Nothing here needs your attention.")
            }
        }
    }

    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this file?") },
            text = { Text("${file.path}${file.name} (${size(file.size)}) will be deleted permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Care.deleteFile(context, file.uri) }
                        if (ok) {
                            deleted.add(file.uri)
                            failed = null
                        } else {
                            failed = "${file.name} couldn't be deleted."
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Keep") } },
        )
    }
}

// ---------------------------------------------------------------- phone health

@Composable
private fun HealthCard(health: Health?) {
    val context = LocalContext.current
    fun size(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    Section("Phone health", Help.HEALTH) {
        if (health == null) {
            Muted("Looking…")
            return@Section
        }
        Text(
            "Memory: ${size(health.ramAvailable)} available of ${size(health.ramTotal)}" +
                if (health.lowMemory) ". Android reports it is running low." else ""
        )

        val battery = buildString {
            append("Battery: ")
            append(if (health.batteryPercent >= 0) "${health.batteryPercent}%" else "level not reported")
            append(", condition ${health.batteryHealth.lowercase()}")
            health.batteryTempC?.let { append(", %.0f°C".format(it)) }
        }
        Text(battery)
        if ((health.batteryTempC ?: 0f) >= 45f) {
            Text(
                "The battery is hot. Heat makes a phone slow itself down and wears the battery.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        LinkRow("Battery use by app", "Opens Android's battery screen") { Care.openBattery(context) }

        val age = health.patchAgeDays
        Text(
            "Android ${health.androidVersion}, security update ${health.securityPatch}" +
                if (age != null) " (${age} days old)" else ""
        )
        if (age != null && age > 120) {
            Text(
                "This phone hasn't had a security update in over four months. Check for one below.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        LinkRow("System update", "Check for Android updates") { Care.openSystemUpdate(context) }
    }
}

private fun ago(time: Long): String =
    if (time <= 0L) "never"
    else DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
