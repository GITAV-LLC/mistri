package org.gitav.paindu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gitav.paindu.MainActivity

enum class Screen { Browser, Care, Settings }

@Composable
fun PainduApp(activity: MainActivity) {
    var screen by rememberSaveable { mutableStateOf(Screen.Browser) }
    val openUrl: (String) -> Unit = { url ->
        activity.browser.addTab(url)
        screen = Screen.Browser
    }

    when (screen) {
        Screen.Browser -> BrowserScreen(
            browser = activity.browser,
            settings = activity.settings,
            shield = activity.shield,
            openCare = { screen = Screen.Care },
            openSettings = { screen = Screen.Settings },
        )
        Screen.Care -> {
            BackHandler { screen = Screen.Browser }
            CareScreen(activity.signatures, activity.resumeTick, onBack = { screen = Screen.Browser })
        }
        Screen.Settings -> {
            BackHandler { screen = Screen.Browser }
            SettingsScreen(activity, openUrl = openUrl, onBack = { screen = Screen.Browser })
        }
    }

    // Leaving a full-screen video comes before any other meaning of Back.
    BackHandler(enabled = activity.isFullscreen) { activity.hideFullscreen() }
}

/**
 * The frame shared by Device care and Settings: a title bar with Back, a row of tabs,
 * and the chosen tab's sections in a scrolling column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedPage(
    title: String,
    onBack: () -> Unit,
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val ground = MaterialTheme.colorScheme.background
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = ground,
        topBar = {
            Column(Modifier.background(ground)) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the browser")
                        }
                    },
                    actions = { actions() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ground),
                )
                val tabItems: @Composable () -> Unit = {
                    tabs.forEachIndexed { index, name ->
                        Tab(
                            selected = index == selected,
                            onClick = { onSelect(index) },
                            text = { Text(name) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tabs.size <= 3) {
                    TabRow(selectedTabIndex = selected, containerColor = ground, tabs = tabItems)
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = selected,
                        containerColor = ground,
                        edgePadding = 8.dp,
                        tabs = tabItems,
                    )
                }
            }
        },
    ) { padding ->
        // key(): each tab gets its own scroll position, starting at the top.
        key(selected) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                content = content,
            )
        }
    }
}
