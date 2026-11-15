package org.gitav.paindu.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.gitav.paindu.R
import org.gitav.paindu.Settings
import org.gitav.paindu.browser.Browser
import org.gitav.paindu.browser.Tab
import org.gitav.paindu.shield.FilterLists
import org.gitav.paindu.shield.ShieldEngine

private enum class Sheet { None, Shield, Tabs, Bookmarks, History }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    browser: Browser,
    settings: Settings,
    shield: ShieldEngine,
    openCare: () -> Unit,
    openSettings: () -> Unit,
) {
    val tab = browser.current ?: return
    val context = LocalContext.current
    var sheet by remember { mutableStateOf(Sheet.None) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Short messages from the browser, with at most one optional action.
    val notice = browser.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            val result = snackbar.showSnackbar(
                message = notice.text,
                actionLabel = notice.actionLabel,
                withDismissAction = notice.actionLabel != null,
                duration = if (notice.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) notice.action?.invoke()
            if (browser.notice === notice) browser.notice = null
        }
    }

    BackHandler(enabled = !tab.isHome) { browser.back(tab) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = { sheet = Sheet.Shield }) {
                        BadgedBox(badge = { if (tab.blocked > 0) Badge { Text(countLabel(tab.blocked)) } }) {
                            Icon(
                                painterResource(R.drawable.ic_shield),
                                contentDescription = "Shield. ${tab.blocked} blocked on this page.",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    AddressField(tab, onGo = { browser.load(tab, it) }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { sheet = Sheet.Tabs }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(R.drawable.ic_tabs),
                                contentDescription = "Tabs. ${browser.tabs.size} open.",
                            )
                            Text(countLabel(browser.tabs.size), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        BrowserMenu(
                            open = menuOpen,
                            close = { menuOpen = false },
                            tab = tab,
                            browser = browser,
                            settings = settings,
                            show = { sheet = it },
                            openCare = openCare,
                            openSettings = openSettings,
                            share = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, tab.url)
                                context.startActivity(Intent.createChooser(send, null))
                            },
                        )
                    }
                }
                if (!tab.isHome && tab.progress < 100) {
                    LinearProgressIndicator(
                        progress = { tab.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (tab.isHome) {
                HomePage(settings, shield, openCare, openShield = { sheet = Sheet.Shield }) { browser.load(tab, it) }
            } else {
                WebContainer(tab)
            }
        }
    }

    if (sheet != Sheet.None) {
        ModalBottomSheet(onDismissRequest = { sheet = Sheet.None }) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                when (sheet) {
                    Sheet.Shield -> ShieldSheet(tab, browser, settings, shield) {
                        sheet = Sheet.None
                        openSettings()
                    }
                    Sheet.Tabs -> TabsSheet(browser) { sheet = Sheet.None }
                    Sheet.Bookmarks -> LinksSheet(
                        title = "Bookmarks",
                        empty = "No bookmarks yet. Use the menu to bookmark a page.",
                        links = settings.bookmarks,
                        onOpen = {
                            browser.load(tab, it)
                            sheet = Sheet.None
                        },
                        onRemove = { settings.toggleBookmark("", it) },
                    )
                    Sheet.History -> LinksSheet(
                        title = "History",
                        empty = "No history yet.",
                        links = settings.history,
                        onOpen = {
                            browser.load(tab, it)
                            sheet = Sheet.None
                        },
                        onClearAll = { settings.clearHistory() },
                    )
                    Sheet.None -> {}
                }
            }
        }
    }

    browser.pendingDownload?.let { request ->
        AlertDialog(
            onDismissRequest = { browser.pendingDownload = null },
            title = { Text("Download this file?") },
            text = {
                val size = if (request.size > 0) " (${Formatter.formatShortFileSize(context, request.size)})" else ""
                Text(
                    "${request.name}$size\n\npaindu checks the file against known malware before it is " +
                        "saved to your Downloads folder."
                )
            },
            confirmButton = { TextButton(onClick = { browser.startDownload(request) }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { browser.pendingDownload = null }) { Text("Cancel") } },
        )
    }

    browser.heldMalware?.let { held ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("This file matches known malware") },
            text = {
                Text(
                    "${held.name} has the same fingerprint as a file in the abuse.ch MalwareBazaar " +
                        "database. It has not been saved to Downloads.\n\nDeleting it is the safe choice."
                )
            },
            confirmButton = { TextButton(onClick = { browser.deleteHeldMalware() }) { Text("Delete it") } },
            dismissButton = { TextButton(onClick = { browser.keepHeldMalware() }) { Text("Keep anyway") } },
        )
    }
}

private fun countLabel(n: Int): String = if (n > 99) "99+" else n.toString()

/** Hosts the current tab's WebView. Each tab keeps its own WebView alive while it is open. */
@Composable
private fun WebContainer(tab: Tab) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> FrameLayout(ctx) },
        update = { box ->
            val wv = tab.webView
            if (wv.parent !== box) {
                box.removeAllViews()
                (wv.parent as? ViewGroup)?.removeView(wv)
                box.addView(
                    wv,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        },
    )
}

@Composable
private fun AddressField(tab: Tab, onGo: (String) -> Unit, modifier: Modifier = Modifier) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Show the page's address unless the user is in the middle of typing.
    LaunchedEffect(tab.id, tab.url, focused) {
        if (!focused) value = TextFieldValue(tab.url)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (value.text.isEmpty()) {
                Text(
                    "Search or type a web address",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    onGo(value.text)
                    focusManager.clearFocus()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        if (state.isFocused) value = value.copy(selection = TextRange(0, value.text.length))
                    }
                    .semantics { contentDescription = "Address bar" },
            )
        }
    }
}

@Composable
private fun BrowserMenu(
    open: Boolean,
    close: () -> Unit,
    tab: Tab,
    browser: Browser,
    settings: Settings,
    show: (Sheet) -> Unit,
    openCare: () -> Unit,
    openSettings: () -> Unit,
    share: () -> Unit,
) {
    DropdownMenu(expanded = open, onDismissRequest = close) {
        val onPage = !tab.isHome
        Row(Modifier.padding(horizontal = 4.dp)) {
            IconButton(enabled = tab.canBack, onClick = { close(); tab.webView.goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(enabled = tab.canForward, onClick = { close(); tab.webView.goForward() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(enabled = onPage, onClick = { close(); tab.webView.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
            }
        }
        HorizontalDivider()
        MenuItem("New tab", close) { browser.addTab() }
        MenuItem("Bookmarks", close) { show(Sheet.Bookmarks) }
        MenuItem("History", close) { show(Sheet.History) }
        MenuItem(if (settings.isBookmarked(tab.url)) "Remove bookmark" else "Bookmark this page", close, onPage) {
            settings.toggleBookmark(tab.title, tab.url)
        }
        MenuItem(if (tab.reader) "Leave reader view" else "Reader view", close, onPage) { browser.toggleReader(tab) }
        MenuItem(if (browser.speaking) "Stop reading aloud" else "Read aloud", close, onPage || browser.speaking) {
            browser.toggleReadAloud(tab)
        }
        MenuItem("Share", close, onPage, share)
        HorizontalDivider()
        MenuItem("Device care", close, action = openCare)
        MenuItem("Settings", close, action = openSettings)
    }
}

@Composable
private fun MenuItem(label: String, close: () -> Unit, enabled: Boolean = true, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        enabled = enabled,
        onClick = {
            close()
            action()
        },
    )
}

@Composable
private fun HomePage(
    settings: Settings,
    shield: ShieldEngine,
    openCare: () -> Unit,
    openShield: () -> Unit,
    open: (String) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Image(painterResource(R.drawable.paindu_logo), contentDescription = null, modifier = Modifier.size(96.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "paindu",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text("پینڈو", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("by GITAV", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))

        HomeTile(
            icon = painterResource(R.drawable.ic_shield),
            title = "Shield",
            detail = "On. %,d ad, tracker and malware domains on the block lists.".format(shield.totalDomains),
            button = "Open",
            onClick = openShield,
        )
        HomeTile(
            icon = rememberVectorPainter(Icons.Filled.Build),
            title = "Device care",
            detail = "Check apps for malware, see who has powerful access, and find what's using space.",
            button = "Open",
            onClick = openCare,
        )

        if (settings.bookmarks.isNotEmpty()) {
            SectionCard("Bookmarks", rememberVectorPainter(Icons.Filled.Star)) {
                for ((index, link) in settings.bookmarks.take(8).withIndex()) {
                    if (index > 0) RowDivider()
                    ActionRow(link.title, hostOf(link.url), "Open") { open(link.url) }
                }
            }
        }
    }
}

/** A home-screen tile: what it is on the left, a real button on the right. */
@Composable
private fun HomeTile(icon: Painter, title: String, detail: String, button: String, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Muted(detail)
            }
            Button(
                onClick = onClick,
                modifier = Modifier.semantics { contentDescription = "$button: $title" },
            ) { Text(button) }
        }
    }
}

private fun hostOf(url: String): String = Uri.parse(url).host?.removePrefix("www.") ?: url

@Composable
private fun ShieldSheet(tab: Tab, browser: Browser, settings: Settings, shield: ShieldEngine, openSettings: () -> Unit) {
    val host = FilterLists.cleanHost(Uri.parse(tab.url).host).removePrefix("www.")
    Text("Shield", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    if (tab.isHome || host.isEmpty()) {
        Muted("Open a page to see what paindu blocks on it.")
    } else {
        val paused = shield.isPaused(host)
        Text(
            if (paused) "Protection is paused on $host."
            else "${tab.blocked} ad, tracker or malware requests blocked on this page.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            label = "Pause on $host",
            checked = paused,
            help = Help.PAUSE,
            onChange = {
                settings.togglePaused(host)
                browser.applySettings()
                tab.webView.reload()
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    Muted("%,d domains on the block lists.".format(shield.totalDomains))
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = openSettings) { Text("Shield settings") }
}

@Composable
private fun TabsSheet(browser: Browser, close: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Tabs",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        FilledTonalButton(onClick = {
            browser.addTab()
            close()
        }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("New tab", Modifier.padding(start = 8.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn {
        items(browser.tabs, key = { it.id }) { t ->
            val selected = t === browser.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(role = Role.Tab) {
                        browser.select(t)
                        close()
                    }
                    .padding(start = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (t.isHome) "New tab" else t.title.ifBlank { t.url },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (!t.isHome) Muted(hostOf(t.url))
                }
                IconButton(onClick = { browser.closeTab(t) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close tab")
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun LinksSheet(
    title: String,
    empty: String,
    links: List<org.gitav.paindu.Link>,
    onOpen: (String) -> Unit,
    onRemove: ((String) -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onClearAll != null && links.isNotEmpty()) OutlinedButton(onClick = onClearAll) { Text("Clear all") }
    }
    Spacer(Modifier.height(8.dp))
    if (links.isEmpty()) {
        Muted(empty)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(links) { link ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ActionRow(link.title, hostOf(link.url), "Open") { onOpen(link.url) }
                }
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(link.url) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${link.title}")
                    }
                }
            }
        }
    }
}
