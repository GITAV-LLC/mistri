package org.gitav.paindu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import org.gitav.paindu.browser.Browser
import org.gitav.paindu.care.Signatures
import org.gitav.paindu.shield.ShieldEngine
import org.gitav.paindu.ui.PainduApp
import org.gitav.paindu.ui.PainduTheme

class MainActivity : ComponentActivity() {
    lateinit var settings: Settings
        private set
    lateinit var shield: ShieldEngine
        private set
    lateinit var signatures: Signatures
        private set
    lateinit var browser: Browser
        private set

    /** Goes up each time the app comes back to the front, so screens can re-check what changed. */
    var resumeTick by mutableIntStateOf(0)
        private set

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    private var fullscreenView: View? = null

    /** True while a video is playing full screen. */
    var isFullscreen by mutableStateOf(false)
        private set
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = Settings(applicationContext)
        shield = ShieldEngine(applicationContext, settings)
        signatures = Signatures(applicationContext, settings)
        browser = Browser(this, settings, shield, signatures)
        browser.restore(linkFrom(intent))

        // Load the block lists, then refresh them if they are more than a day old.
        // This only ever happens while the app is open; nothing is scheduled in the background.
        browser.scope.launch {
            shield.load()
            if (settings.autoUpdate.value && shield.isStale()) shield.update()
        }

        setContent {
            // Keep the clock and battery icons readable when the phone switches between
            // light and dark while paindu is open.
            val dark = isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            PainduTheme {
                PainduApp(this)
            }
        }
    }

    private fun linkFrom(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_VIEW) intent.dataString else null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { browser.addTab(it) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick += 1
    }

    override fun onPause() {
        super.onPause()
        browser.saveSession()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }

    /** A page asked to upload a file: show the system file picker. */
    fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            filePicker.launch(params.createIntent())
            true
        } catch (e: Exception) {
            fileCallback = null
            false
        }
    }

    /** Full-screen video. */
    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        isFullscreen = true
        (window.decorView as ViewGroup).addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        view.setBackgroundColor(android.graphics.Color.BLACK)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun hideFullscreen() {
        val view = fullscreenView ?: return
        (window.decorView as ViewGroup).removeView(view)
        fullscreenView = null
        isFullscreen = false
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }
}
