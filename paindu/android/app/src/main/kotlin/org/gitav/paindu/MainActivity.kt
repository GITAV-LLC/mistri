package org.gitav.paindu

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.gitav.paindu.browser.Browser
import org.gitav.paindu.care.Signatures
import org.gitav.paindu.shield.ShieldEngine
import org.gitav.paindu.ui.PainduApp
import org.gitav.paindu.ui.PainduTheme

/**
 * paindu's one screen-holder. It extends FragmentActivity only because Android's
 * screen-lock prompt (androidx.biometric) requires it.
 */
class MainActivity : FragmentActivity() {
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

    // ---- default browser
    /** Whether paindu is the phone's default browser right now. */
    var isDefaultBrowser by mutableStateOf(false)
        private set
    private val rolePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshDefaultBrowser()
    }

    // ---- screen lock
    /** True while paindu is waiting for the phone's screen lock. */
    var locked by mutableStateOf(false)
        private set
    private var leftAt = 0L

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
        locked = settings.appLock.value
        refreshDefaultBrowser()

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

    override fun onStart() {
        super.onStart()
        // Lock again after paindu has been out of sight for a minute.
        if (settings.appLock.value && leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > LOCK_AFTER_MS) {
            locked = true
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::browser.isInitialized) browser.onTrimMemory(level)
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultBrowser()
        browser.tidyMemory()
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

    // ------------------------------------------------------------ default browser

    private fun refreshDefaultBrowser() {
        isDefaultBrowser = try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = getSystemService(RoleManager::class.java)
                roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && roles.isRoleHeld(RoleManager.ROLE_BROWSER)
            } else {
                val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
                packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Show Android's own "set as default browser" question. Only ever called from a
     * button the user pressed; paindu never asks on its own.
     */
    fun requestDefaultBrowser() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    rolePicker.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                    return
                }
            }
            openDefaultAppsSettings()
        } catch (e: Exception) {
            openDefaultAppsSettings()
        }
    }

    fun openDefaultAppsSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                // No settings app to open; nothing more we can do.
            }
        }
    }

    // ------------------------------------------------------------ screen lock

    /** True when the phone has a PIN, pattern, password or biometric that paindu can ask for. */
    fun canUseScreenLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(LOCK_METHODS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Show Android's own unlock prompt: fingerprint or face if set up, otherwise PIN, pattern or password. */
    private fun authenticate(title: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Use your fingerprint, face or screen lock")
                .setAllowedAuthenticators(LOCK_METHODS)
                .build()
        )
    }

    fun unlock() {
        if (!canUseScreenLock()) {
            // The phone's screen lock has been removed, so there is nothing left to ask for.
            settings.appLock.value = false
            locked = false
            return
        }
        authenticate("Unlock paindu") { locked = false }
    }

    /**
     * Turn the lock on or off. Either way the user proves it's them first.
     * Returns false straight away if the phone has no screen lock to use.
     */
    fun setAppLock(on: Boolean): Boolean {
        if (!canUseScreenLock()) {
            if (!on) settings.appLock.value = false
            return !on
        }
        authenticate(if (on) "Turn on the paindu lock" else "Turn off the paindu lock") {
            settings.appLock.value = on
        }
        return true
    }

    fun openScreenLockSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
        } catch (e: Exception) {
            // No settings app to open.
        }
    }

    /**
     * Keep paindu out of screenshots and the recent-apps preview while the lock is on
     * or a whush tab is showing.
     */
    fun setPrivateWindow(secure: Boolean) {
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    companion object {
        private const val LOCK_AFTER_MS = 60_000L
        private const val LOCK_METHODS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
