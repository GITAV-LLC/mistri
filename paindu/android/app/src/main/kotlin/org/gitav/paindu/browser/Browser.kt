package org.gitav.paindu.browser

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.gitav.paindu.MainActivity
import org.gitav.paindu.Settings
import org.gitav.paindu.care.Signatures
import org.gitav.paindu.plugins.Plugins
import org.gitav.paindu.shield.FilterLists
import org.gitav.paindu.shield.ShieldEngine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** One browser tab. The properties Compose reads are snapshot state. */
class Tab(val id: Int, val whush: Boolean = false) {
    /** The page itself. Null for a blank tab and while the tab is asleep. */
    var view: WebView? = null

    /** True when the page has been put away to save memory. Its address and position are kept. */
    var asleep by mutableStateOf(false)

    /** True when the page stopped working; it reloads only when the user asks. */
    var crashed by mutableStateOf(false)
    var saved: Bundle? = null
    var lastActive: Long = SystemClock.elapsedRealtime()

    var title by mutableStateOf("")
    var url by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var blocked by mutableIntStateOf(0)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var reader by mutableStateOf(false)

    val isHome: Boolean get() = url.isEmpty()

    // Read from WebView's background threads.
    @Volatile var pageHost: String = ""
    @Volatile var openedByPageAt: Long = 0L
    @Volatile var warnedHost: String = ""
    val blockedCounter = AtomicInteger(0)

    var readerPending = false
    var script: ScriptHandler? = null
}

/** How short of memory the phone says it is. */
enum class Pressure { NONE, LOW, CRITICAL }

/** A short message for the user, optionally with one action they can choose to take. */
class Notice(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/**
 * The browser: tabs, navigation, and Shield wired into every page load.
 * Pages are rendered by Android System WebView (Chromium), which Google keeps updated.
 */
class Browser(
    private val activity: MainActivity,
    private val settings: Settings,
    private val shield: ShieldEngine,
    signatures: Signatures,
) {
    val tabs = mutableStateListOf<Tab>()
    var currentIndex by mutableIntStateOf(0)
        private set
    val current: Tab? get() = tabs.getOrNull(currentIndex)

    var notice by mutableStateOf<Notice?>(null)
    var pendingDownload by mutableStateOf<DownloadRequest?>(null)
    var heldMalware by mutableStateOf<DownloadResult.Malware?>(null)
    var speaking by mutableStateOf(false)
        private set

    val scope = MainScope()
    private val downloader = Downloader(activity.applicationContext, signatures)
    private val main = Handler(Looper.getMainLooper())
    private val token = UUID.randomUUID().toString().replace("-", "")
    private var nextId = 1
    private var lastWindowAt = 0L
    private var tts: TextToSpeech? = null
    private val readabilityJs: String by lazy {
        activity.assets.open("Readability.js").bufferedReader().use { it.readText() }
    }

    // ------------------------------------------------------------ tabs

    fun addTab(url: String? = null, select: Boolean = true, whush: Boolean = false): Tab {
        val tab = Tab(nextId++, whush)
        tabs.add(tab)
        if (select) select(tab)
        if (!url.isNullOrBlank()) load(tab, url)
        return tab
    }

    fun select(tab: Tab) {
        val i = tabs.indexOf(tab)
        if (i < 0) return
        current?.lastActive = SystemClock.elapsedRealtime()
        currentIndex = i
        tab.lastActive = SystemClock.elapsedRealtime()
        tidyMemory()
    }

    fun closeTab(tab: Tab) {
        val i = tabs.indexOf(tab)
        if (i < 0) return
        tabs.removeAt(i)
        destroy(tab)
        if (tab.whush) forgetWhushIfDone()
        if (tabs.isEmpty()) {
            addTab()
            return
        }
        if (i < currentIndex) currentIndex -= 1
        currentIndex = currentIndex.coerceIn(0, tabs.lastIndex)
    }

    private fun destroy(tab: Tab) {
        val wv = tab.view ?: return
        tab.view = null
        tab.script = null
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.stopLoading()
        if (tab.whush) wv.clearCache(true) // whush pages leave nothing in the cache either
        wv.destroy()
    }

    /**
     * The tab's page, created or woken if need be. A blank tab has no page at all
     * until something is loaded in it, and a sleeping tab gets its page back here.
     */
    fun pageOf(tab: Tab): WebView {
        tab.view?.let { return it }
        val wv = buildView(tab)
        tab.view = wv
        val saved = tab.saved
        tab.saved = null
        val restored = saved != null && wv.restoreState(saved) != null
        if (!restored && tab.url.isNotEmpty()) wv.loadUrl(tab.url)
        tab.asleep = false
        tab.crashed = false
        return wv
    }

    // ------------------------------------------------------------ sleeping tabs

    /**
     * Put a background tab's page away. A page costs tens to hundreds of megabytes;
     * its address, title and back/forward position cost almost nothing, and that is
     * all that is kept. The page comes back when the tab is opened again.
     */
    fun sleep(tab: Tab) {
        val wv = tab.view ?: return
        if (tab === current) return
        if (tab.url.isNotEmpty()) {
            val state = Bundle()
            tab.saved = if (wv.saveState(state) != null) state else null
            tab.asleep = true
        }
        destroy(tab)
    }

    /** How many background tabs may stay awake on this phone when memory isn't tight. */
    private val awakeBudget: Int by lazy {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        (info.totalMem / 1_000_000_000.0 * 1.5).toInt().coerceIn(2, 8)
    }

    private val activityManager: ActivityManager
        get() = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Decide which background tabs should sleep. This goes by what the phone is
     * actually short of, not by a fixed number of tabs:
     *  - when Android says memory is critical, every background tab sleeps;
     *  - when memory is low (Android says so, or under an eighth is free), only the
     *    most recently used background tab stays awake;
     *  - otherwise a phone keeps awake as many as its RAM comfortably allows
     *    (2 on a small phone, up to 8 on a large one), and any tab not looked at
     *    for half an hour sleeps regardless.
     */
    fun tidyMemory(pressure: Pressure = Pressure.NONE) {
        val now = SystemClock.elapsedRealtime()
        val awake = tabs.filter { it !== current && it.view != null }.sortedBy { it.lastActive }
        if (awake.isEmpty()) return
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val tight = info.lowMemory || info.availMem < info.totalMem / 8
        val keep = when {
            pressure == Pressure.CRITICAL -> 0
            pressure == Pressure.LOW || tight -> 1
            else -> awakeBudget
        }
        val keepers = awake.takeLast(keep).filter { now - it.lastActive < IDLE_SLEEP_MS }
        for (tab in awake) if (tab !in keepers) sleep(tab)
    }

    /** Android's own memory warnings, passed on by the activity. */
    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        val pressure = when {
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> Pressure.NONE
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> Pressure.CRITICAL
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> Pressure.CRITICAL
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> Pressure.LOW
            else -> Pressure.NONE
        }
        tidyMemory(pressure)
    }

    // ------------------------------------------------------------ whush (private tabs)

    /** whush needs a web engine recent enough to keep separate profiles. */
    val whushSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun addWhushTab(): Tab? {
        if (!whushSupported) {
            notice = Notice("whush needs a newer Android System WebView. Update it in the Play Store.")
            return null
        }
        return addTab(whush = true)
    }

    fun closeWhushTabs() {
        tabs.filter { it.whush }.forEach { closeTab(it) }
    }

    /**
     * The compartment the current whush tabs share. Each whush session gets a brand-new
     * one, so a new session can never see anything from an earlier one.
     */
    private var whushProfile: String? = null

    private fun whushProfileName(): String =
        whushProfile ?: ("$WHUSH_PREFIX${UUID.randomUUID().toString().take(8)}").also { whushProfile = it }

    /**
     * Once the last whush tab is closed, wipe what the session stored: cookies, site
     * data and cache. The web engine won't let a compartment be removed outright while
     * the app that used it is still running, so the emptied shell is removed the next
     * time paindu starts (see [removeOldWhushProfiles]).
     */
    private fun forgetWhushIfDone() {
        if (!whushSupported || tabs.any { it.whush }) return
        val name = whushProfile ?: return
        whushProfile = null
        try {
            val profile = ProfileStore.getInstance().getProfile(name) ?: return
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.flush()
            profile.webStorage.deleteAllData()
        } catch (e: Exception) {
            // Nothing more can be done now; the whole compartment goes at the next start.
        }
    }

    /** At start-up, before anything has used them, remove compartments left by earlier whush sessions. */
    private fun removeOldWhushProfiles() {
        if (!whushSupported) return
        try {
            val store = ProfileStore.getInstance()
            for (name in store.allProfileNames) {
                if (name.startsWith(WHUSH_PREFIX)) {
                    try {
                        store.deleteProfile(name)
                    } catch (e: Exception) {
                        // Already emptied; it will be tried again next time.
                    }
                }
            }
        } catch (e: Exception) {
            // The web engine isn't ready to list profiles; try again next start.
        }
    }

    private fun cookiesFor(tab: Tab): CookieManager =
        if (tab.whush) ProfileStore.getInstance().getOrCreateProfile(whushProfileName()).cookieManager
        else CookieManager.getInstance()

    // ------------------------------------------------------------ session

    /** Reopen the tabs from last time, plus a link another app asked us to open. */
    fun restore(incoming: String?) {
        removeOldWhushProfiles()
        if (settings.restoreTabs.value) {
            try {
                val saved = JSONArray(settings.prefs.getString("session", "[]") ?: "[]")
                for (i in 0 until minOf(saved.length(), MAX_RESTORED_TABS)) {
                    // Restored tabs start asleep: only the one being looked at loads.
                    val tab = Tab(nextId++)
                    val url = saved.optString(i)
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        tab.url = url
                        tab.title = Uri.parse(url).host ?: url
                        tab.asleep = true
                    }
                    tabs.add(tab)
                }
                val index = settings.prefs.getInt("sessionIndex", 0)
                if (tabs.isNotEmpty()) currentIndex = index.coerceIn(0, tabs.lastIndex)
            } catch (e: Exception) {
                // A damaged session is simply not restored.
            }
        }
        if (!incoming.isNullOrBlank()) addTab(incoming)
        if (tabs.isEmpty()) addTab()
    }

    fun saveSession() {
        val kept = tabs.filter { !it.whush } // whush tabs are never written down
        val arr = JSONArray()
        for (t in kept) arr.put(t.url)
        val index = kept.indexOf(current).coerceAtLeast(0)
        settings.prefs.edit().putString("session", arr.toString()).putInt("sessionIndex", index).apply()
    }

    fun destroy() {
        stopSpeaking()
        tts?.shutdown()
        tts = null
        scope.cancel()
        tabs.toList().forEach { destroy(it) }
        tabs.clear()
        forgetWhushIfDone()
    }

    /** Switch a plugin on or off, with whatever tidying up that needs. */
    fun setPlugin(id: String, on: Boolean) {
        settings.setPlugin(id, on)
        if (!on && id == Plugins.HISTORY) settings.clearHistory()
        if (!on && id == Plugins.WHUSH) closeWhushTabs()
    }

    // ------------------------------------------------------------ navigation

    /** Turn what was typed into an address: a web address is opened, anything else is searched for. */
    fun toUrl(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return HOME
        val lower = t.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return t
        if (lower.startsWith("about:")) return HOME
        val looksLikeAddress = !t.contains(' ') && !t.contains("://") &&
            (t.contains('.') || lower == "localhost" || lower.startsWith("localhost:"))
        if (looksLikeAddress) return "https://$t"
        return settings.searchPrefix() + URLEncoder.encode(t, "UTF-8")
    }

    fun load(tab: Tab, input: String) {
        val url = toUrl(input)
        if (url == HOME) goHome(tab) else pageOf(tab).loadUrl(url)
    }

    fun goHome(tab: Tab) {
        tab.url = ""
        tab.title = ""
        tab.blocked = 0
        tab.reader = false
        tab.asleep = false
        tab.crashed = false
        tab.saved = null
        // A blank tab needs no page at all. Let any callback that led here finish first.
        main.post { if (tab.isHome) destroy(tab) }
    }

    fun back(tab: Tab) {
        val wv = tab.view
        if (wv != null && wv.canGoBack()) wv.goBack() else goHome(tab)
    }

    /** Re-apply settings that are baked into each tab (page script, text size, Safe Browsing). */
    fun applySettings() {
        for (tab in tabs) {
            val wv = tab.view ?: continue
            installScript(tab)
            wv.settings.textZoom = pageTextZoom()
            wv.settings.safeBrowsingEnabled = settings.safeBrowsing.value
        }
    }

    private fun pageTextZoom(): Int =
        (settings.textScale * activity.resources.configuration.fontScale).roundToInt().coerceIn(50, 300)

    fun clearBrowsingData() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        for (tab in tabs) {
            tab.saved = null
            val wv = tab.view ?: continue
            wv.clearCache(true)
            wv.clearFormData()
        }
        settings.clearHistory()
        notice = Notice("Cookies, site data, cache and history cleared.")
    }

    // ------------------------------------------------------------ reader view and read aloud

    fun toggleReader(tab: Tab) {
        if (tab.reader) {
            back(tab)
            return
        }
        val pageUrl = tab.url
        extractArticle(tab) { article ->
            val content = article?.optString("c").orEmpty()
            if (content.isBlank()) {
                notice = Notice("Reader view couldn't find an article on this page.")
            } else {
                tab.readerPending = true
                showHtml(tab, pageUrl, readerPage(article?.optString("t").orEmpty(), content))
            }
        }
    }

    /**
     * Show a page from text (reader view), treated as if it had come from [baseUrl].
     * Such a page never passes through shouldInterceptRequest as a navigation, so what
     * requests are judged against is set here, before the page can ask for anything.
     */
    fun showHtml(tab: Tab, baseUrl: String, html: String) {
        val host = Uri.parse(baseUrl).host?.lowercase() ?: ""
        tab.pageHost = host
        if (host != tab.warnedHost) tab.warnedHost = ""
        tab.blockedCounter.set(0)
        tab.blocked = 0
        pageOf(tab).loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", baseUrl)
    }

    fun toggleReadAloud(tab: Tab) {
        if (speaking) {
            stopSpeaking()
            return
        }
        extractArticle(tab) { article ->
            val text = article?.optString("x").orEmpty().trim()
            if (text.isEmpty()) notice = Notice("Couldn't find text to read on this page.") else speak(text)
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        speaking = false
    }

    private fun extractArticle(tab: Tab, done: (JSONObject?) -> Unit) {
        val js = "(function(){\n$readabilityJs\ntry{var a=new Readability(document.cloneNode(true)).parse();" +
            "return a?JSON.stringify({t:a.title||'',c:a.content||'',x:a.textContent||''}):'';}" +
            "catch(e){return '';}})()"
        pageOf(tab).evaluateJavascript(js) { raw ->
            val article = try {
                val inner = JSONTokener(raw ?: "null").nextValue() as? String
                if (inner.isNullOrEmpty()) null else JSONObject(inner)
            } catch (e: Exception) {
                null
            }
            done(article)
        }
    }

    private fun readerPage(title: String, content: String): String =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; " +
            "img-src https: data:; media-src https:; style-src 'unsafe-inline'\">" +
            "<title>${TextUtils.htmlEncode(title)}</title><style>" +
            "body{max-width:40em;margin:0 auto;padding:20px;font:1.15rem/1.7 Georgia,serif;" +
            "background:#F7F2E7;color:#2A2118;overflow-wrap:break-word}" +
            "h1{font-size:1.6em;line-height:1.25}img,video,figure{max-width:100%;height:auto;margin:0}" +
            "a{color:#1F5A32}pre{overflow-x:auto}" +
            "@media (prefers-color-scheme: dark){body{background:#15130E;color:#EDE6D6}a{color:#A8CE8C}}" +
            "</style></head><body><h1>${TextUtils.htmlEncode(title)}</h1>$content</body></html>"

    private fun speak(text: String) {
        val chunks = chunksForSpeech(text)
        fun start(engine: TextToSpeech) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == LAST) main.post { speaking = false }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { speaking = false }
                }
            })
            chunks.forEachIndexed { i, chunk ->
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                engine.speak(chunk, mode, null, if (i == chunks.lastIndex) LAST else "part$i")
            }
            speaking = true
        }

        val ready = tts
        if (ready != null) {
            start(ready)
            return
        }
        var created: TextToSpeech? = null
        created = TextToSpeech(activity.applicationContext) { status ->
            val engine = created
            if (status == TextToSpeech.SUCCESS && engine != null) {
                tts = engine
                start(engine)
            } else {
                notice = Notice("Read aloud isn't available: this phone has no text-to-speech voice installed.")
            }
        }
    }

    // ------------------------------------------------------------ downloads

    fun startDownload(request: DownloadRequest) {
        pendingDownload = null
        notice = Notice("Downloading ${request.name}…")
        scope.launch { report(downloader.fetch(request)) }
    }

    fun keepHeldMalware() {
        val held = heldMalware ?: return
        heldMalware = null
        scope.launch { report(downloader.keepAnyway(held)) }
    }

    fun deleteHeldMalware() {
        heldMalware?.held?.delete()
        heldMalware = null
        notice = Notice("The file was deleted. Nothing was saved.")
    }

    private fun report(result: DownloadResult) {
        when (result) {
            is DownloadResult.Saved -> notice = Notice(
                if (result.checked) "${result.name} saved to Downloads. It matches no known malware."
                else "${result.name} saved to Downloads. It wasn't checked: no signature database yet."
            )
            is DownloadResult.Malware -> heldMalware = result
            is DownloadResult.Failed -> notice = Notice("Download failed: ${result.reason}")
        }
    }

    // ------------------------------------------------------------ building a tab

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildView(tab: Tab): WebView {
        val wv = WebView(activity)
        tab.view = wv
        if (tab.whush) {
            // Must happen before the page is used for anything: whush tabs keep their
            // cookies, storage and cache in a profile of their own.
            val compartment = whushProfileName()
            ProfileStore.getInstance().getOrCreateProfile(compartment)
            WebViewCompat.setProfile(wv, compartment)
        }
        // Let Android reclaim a background tab's memory before a visible one's.
        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            safeBrowsingEnabled = settings.safeBrowsing.value
            textZoom = pageTextZoom()
            // Present as an ordinary mobile Chrome so sites don't serve a cut-down "in-app" page.
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, true)
        }
        // One site's cookies are never sent along with requests made from another site.
        cookiesFor(tab).setAcceptThirdPartyCookies(wv, false)
        wv.webViewClient = Client(tab)
        wv.webChromeClient = Chrome(tab)
        wv.setDownloadListener { url, userAgent, contentDisposition, mime, length ->
            if (url.startsWith("http://") || url.startsWith("https://")) {
                pendingDownload = DownloadRequest(
                    url, URLUtil.guessFileName(url, contentDisposition, mime), mime, length, userAgent,
                    cookiesFor(tab).getCookie(url),
                )
            } else {
                notice = Notice("This kind of download isn't supported yet.")
            }
        }
        installScript(tab)
        return wv
    }

    private fun installScript(tab: Tab) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val wv = tab.view ?: return
        tab.script?.remove()
        tab.script = null
        val js = shield.pageScript()
        if (js.isNotEmpty()) {
            tab.script = WebViewCompat.addDocumentStartJavaScript(wv, js, setOf("*"))
        }
    }

    private fun emptyResponse() =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun warningPage(url: String, host: String): String {
        val safeHost = TextUtils.htmlEncode(host)
        val proceed = "https://$INTERNAL_HOST/$token/proceed?u=${Uri.encode(url)}"
        val back = "https://$INTERNAL_HOST/$token/back"
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<title>Blocked: dangerous site</title><style>" +
            ":root{--bg:#F7F2E7;--card:#FFFDF8;--ink:#2A2118;--muted:#574E3F;--warn:#A1401C;--line:#D9D0BA}" +
            "@media (prefers-color-scheme: dark){:root{--bg:#15130E;--card:#1F1B14;--ink:#EDE6D6;" +
            "--muted:#CBC1AB;--warn:#F0916B;--line:#453E30}}" +
            "body{margin:0;min-height:100vh;display:grid;place-items:center;background:var(--bg);" +
            "color:var(--ink);font:1rem/1.5 system-ui,sans-serif;padding:16px;box-sizing:border-box}" +
            "main{max-width:520px;background:var(--card);border:1px solid var(--line);border-radius:14px;padding:24px}" +
            "h1{margin:0 0 6px;font-size:1.4rem;color:var(--warn)}" +
            "code{background:var(--bg);padding:2px 6px;border-radius:5px;word-break:break-all}" +
            ".row{display:flex;gap:10px;flex-wrap:wrap;margin-top:22px}" +
            "a.btn{display:inline-block;min-height:28px;padding:10px 16px;border-radius:8px;" +
            "border:1px solid var(--line);color:var(--ink);text-decoration:none}" +
            "a.back{background:var(--warn);border-color:var(--warn);color:#fff;font-weight:600}" +
            "p.small{color:var(--muted);font-size:.9rem}</style></head><body><main>" +
            "<h1>This site was blocked</h1>" +
            "<p><code>$safeHost</code> is on a public list of sites that spread malware or scams " +
            "(abuse.ch URLhaus / uBlock Badware).</p>" +
            "<p class=\"small\">paindu stopped the page before anything loaded.</p>" +
            "<div class=\"row\"><a class=\"btn back\" href=\"$back\">Go back to safety</a>" +
            "<a class=\"btn\" href=\"$proceed\">I understand the risk – open it this session</a></div>" +
            "</main></body></html>"
    }

    private fun handleInternal(tab: Tab, uri: Uri) {
        val parts = uri.pathSegments
        if (parts.size < 2 || parts[0] != token) return
        when (parts[1]) {
            "back" -> main.post { back(tab) }
            "proceed" -> {
                val target = uri.getQueryParameter("u") ?: return
                val host = Uri.parse(target).host ?: return
                shield.allowOnce(host)
                pageOf(tab).loadUrl(target)
            }
        }
    }

    /** A link that wants to leave the browser (phone, email, another app): the user decides. */
    private fun offerExternal(uri: Uri) {
        val intent = try {
            if (uri.scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            else Intent(Intent.ACTION_VIEW, uri)
        } catch (e: Exception) {
            return
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        notice = Notice("This page wants to open another app.", "Open") {
            try {
                activity.startActivity(intent)
            } catch (e: Exception) {
                notice = Notice("No app on this phone can open that link.")
            }
        }
    }

    private inner class Client(private val tab: Tab) : WebViewClient() {

        // Runs on a background thread for every request the page makes.
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val uri = request.url
            val scheme = uri.scheme ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.lowercase() ?: return null
            if (host == INTERNAL_HOST) return null

            if (request.isForMainFrame) {
                val born = tab.openedByPageAt
                if (born != 0L && SystemClock.elapsedRealtime() - born < POPUP_WINDOW_MS &&
                    settings.popups.value && shield.isPopupTarget(host) && !shield.isPaused(host)
                ) {
                    main.post {
                        closeTab(tab)
                        notice = Notice("Closed a pop-up tab that opened a blocked site.")
                    }
                    return emptyResponse()
                }
                tab.pageHost = host
                tab.blockedCounter.set(0)
                main.post { tab.blocked = 0 }
                tab.warnedHost = ""
                if (shield.shouldWarn(host)) {
                    tab.warnedHost = host
                    val html = warningPage(uri.toString(), host)
                    return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
                }
                return null
            }

            if (shield.shouldBlock(host, tab.pageHost)) {
                // Requests made by our own warning page (its favicon) are dropped but not counted.
                if (tab.warnedHost.isEmpty()) {
                    val n = tab.blockedCounter.incrementAndGet()
                    main.post { tab.blocked = n }
                }
                return emptyResponse()
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val scheme = (uri.scheme ?: "").lowercase()
            if (scheme == "http" || scheme == "https") {
                if (uri.host == INTERNAL_HOST) {
                    handleInternal(tab, uri)
                    return true
                }
                return false
            }
            if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") return false
            if (request.isForMainFrame) offerExternal(uri)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            val address = url ?: ""
            val isWeb = address.startsWith("http://") || address.startsWith("https://")
            val host = if (isWeb) Uri.parse(address).host?.lowercase() ?: "" else ""
            tab.reader = tab.readerPending
            tab.readerPending = false
            // The count of blocked requests is reset where a navigation begins (the main-frame
            // request, or showHtml), not here: this callback can arrive after the new page
            // has already made requests.
            if (isWeb) {
                tab.url = address
                tab.pageHost = host
                // A redirect can land on a listed site without a fresh request we could intercept.
                if (host != tab.warnedHost) {
                    tab.warnedHost = ""
                    if (shield.shouldWarn(host)) {
                        tab.warnedHost = host
                        view.stopLoading()
                        view.loadDataWithBaseURL(address, warningPage(address, host), "text/html", "utf-8", address)
                        return
                    }
                }
            } else if (address == HOME) {
                tab.url = ""
            }
            if (tab.script == null && isWeb) {
                // Older WebView without document-start scripts: inject as early as we can.
                val js = shield.pageScript()
                if (js.isNotEmpty()) view.evaluateJavascript(js, null)
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            val address = url ?: ""
            if (address == HOME) {
                view.clearHistory()
                tab.title = ""
            } else {
                tab.title = view.title ?: ""
                if (address.startsWith("http") && !tab.reader && tab.warnedHost.isEmpty()) {
                    if (!tab.whush && settings.pluginOn(Plugins.HISTORY)) settings.addHistory(tab.title, address)
                }
            }
            tab.canBack = view.canGoBack()
            tab.canForward = view.canGoForward()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            val address = url ?: ""
            if (address.startsWith("http://") || address.startsWith("https://")) {
                // The committed address is what requests are judged against. This also covers
                // redirects and pages shown from text (reader view), which never pass through
                // shouldInterceptRequest as a main-frame request.
                val host = Uri.parse(address).host?.lowercase() ?: ""
                tab.url = address
                tab.pageHost = host
                // Having left a warning page, later pages are ordinary pages again.
                if (host != tab.warnedHost) tab.warnedHost = ""
            }
            tab.canBack = view.canGoBack()
            tab.canForward = view.canGoForward()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // The page's process is gone: either it crashed, or Android took its memory back.
            // Keep the tab and its address; the page reloads when the tab is next opened.
            val crashed = detail.didCrash()
            main.post {
                destroy(tab)
                tab.saved = null
                if (tab.url.isNotEmpty()) {
                    tab.asleep = true
                    tab.crashed = crashed && tab === current
                }
                if (tab.crashed) notice = Notice("This page stopped working.")
            }
            return true
        }
    }

    private inner class Chrome(private val tab: Tab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!tab.isHome) tab.title = title ?: ""
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val now = SystemClock.elapsedRealtime()
            val guard = settings.popups.value && !shield.isPaused(tab.pageHost)
            // The pop-up guard: a new tab needs a real tap, and one tap opens at most one tab.
            if (guard && (!isUserGesture || now - lastWindowAt < 1000)) {
                notice = Notice("Blocked a pop-up.")
                return false
            }
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            lastWindowAt = now
            val opened = addTab(whush = tab.whush)
            opened.openedByPageAt = now
            transport.webView = pageOf(opened)
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            closeTab(tab)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = activity.chooseFiles(filePathCallback, fileChooserParams)

        // Location is never shared: paindu doesn't hold the location permission at all.
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            activity.showFullscreen(view, callback)
        }

        override fun onHideCustomView() {
            activity.hideFullscreen()
        }
    }

    companion object {
        const val HOME = "about:blank"
        private const val INTERNAL_HOST = "paindu.invalid"
        private const val POPUP_WINDOW_MS = 8000L
        private const val MAX_RESTORED_TABS = 12
        private const val LAST = "last"
        private const val WHUSH_PREFIX = "whush-"
        private const val IDLE_SLEEP_MS = 30L * 60 * 1000

        /** Text-to-speech takes a few thousand characters at a time; split on sentence ends. */
        fun chunksForSpeech(text: String, limit: Int = 3000): List<String> {
            val out = ArrayList<String>()
            var rest = text.trim()
            while (rest.length > limit) {
                var cut = rest.lastIndexOf(". ", limit)
                if (cut < limit / 2) cut = rest.lastIndexOf(' ', limit)
                if (cut < limit / 2) cut = limit - 1
                out.add(rest.substring(0, cut + 1).trim())
                rest = rest.substring(cut + 1).trim()
            }
            if (rest.isNotEmpty()) out.add(rest)
            return out
        }
    }
}
