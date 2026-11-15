package org.gitav.mistri.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.gitav.mistri.MainActivity

enum class Screen { Browser, Care, Settings }

@Composable
fun MistriApp(activity: MainActivity) {
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

/** The frame shared by Device care and Settings: a title bar with Back, and a scrolling column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the browser")
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding: PaddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            content()
        }
    }
}
