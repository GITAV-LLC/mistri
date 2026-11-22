package org.gitav.paindu

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.gitav.paindu.care.Care
import org.gitav.paindu.care.HashDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real app on an emulator: it must start, its screens must open, and
 * Shield must do its job inside a real WebView. Pages are loaded from strings,
 * so the tests don't depend on any website.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun start() {
        // A clean slate, no list downloads, and one made-up "malware" site on the URLhaus list.
        val lists = File(context.filesDir, "lists")
        lists.mkdirs()
        File(lists, "urlhaus.txt").writeText("malware-site.test\n")
        // Most tests start past the walkthrough, with every plugin switched on.
        launch(setupDone = true, plugins = listOf("whush", "history", "care"))
    }

    /** Start paindu from a clean slate with the given saved settings. */
    private fun launch(setupDone: Boolean, plugins: List<String> = emptyList(), session: String? = null) {
        if (::scenario.isInitialized) scenario.close()
        val edit = context.getSharedPreferences("paindu", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("autoUpdate", false)
            .putBoolean("setupDone", setupDone)
        for (id in plugins) edit.putBoolean("plugin.$id", true)
        if (session != null) edit.putString("session", session).putInt("sessionIndex", 0)
        edit.commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun stop() {
        scenario.close()
    }

    private fun <T> onActivity(block: (MainActivity) -> T): T {
        val box = arrayOfNulls<Any>(1)
        scenario.onActivity { box[0] = block(it) }
        @Suppress("UNCHECKED_CAST")
        return box[0] as T
    }

    private fun waitFor(what: String, timeoutMs: Long = 45_000, condition: (MainActivity) -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (onActivity(condition)) return
            // Under test, Compose only draws a new frame when asked to; some things
            // (waking a sleeping tab) happen as a result of a frame.
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(250)
        }
        val state = onActivity { a ->
            val t = a.browser.current
            "title='${t?.title}' url='${t?.url}' blocked=${t?.blocked} reader=${t?.reader} domains=${a.shield.totalDomains}"
        }
        throw AssertionError("Timed out waiting for: $what ($state)")
    }

    private fun loadPage(baseUrl: String, html: String) {
        waitFor("block lists loaded") { it.shield.totalDomains > 0 }
        onActivity { it.browser.pageOf(it.browser.current!!).loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", baseUrl) }
    }

    @Test
    fun startsAndOpensEveryScreenAndTab() {
        compose.onNodeWithText("paindu").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open: Device care").performScrollTo().performClick()

        // Device care: Security, Storage, Health
        compose.onNodeWithText("Check apps for malware").assertIsDisplayed()
        compose.onNodeWithText("Apps with powerful access").assertExists()
        compose.onNodeWithText("Storage").performClick()
        compose.onNodeWithText("Check a folder").assertExists()
        compose.onNodeWithText("Health").performClick()
        compose.onNodeWithText("Battery").assertExists()
        compose.onNodeWithText("Security updates").assertExists()
        compose.onNodeWithContentDescription("Back to the browser").performClick()

        // Plugins: Available, Planned
        compose.onNodeWithContentDescription("Open: Plugins").performScrollTo().performClick()
        if (onActivity { it.browser.whushSupported }) compose.onNodeWithText("Use whush").assertExists()
        compose.onNodeWithText("Use History").assertExists()
        compose.onNodeWithText("Planned").performClick()
        compose.onNodeWithText("Passwords").assertExists()
        compose.onNodeWithText("Bring your own VPN").assertExists()
        compose.onNodeWithContentDescription("Back to the browser").performClick()

        // Settings: Shield, Browsing, Reading, Privacy, About
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Block ads").assertIsDisplayed()
        compose.onNodeWithText("Browsing").performScrollTo().performClick()
        compose.onNodeWithText("Default browser").assertIsDisplayed()
        compose.onNodeWithText("Search engine").assertExists()
        compose.onNodeWithText("Sleeping tabs").assertExists()
        compose.onNodeWithText("Reading").performScrollTo().performClick()
        compose.onNodeWithText("Page text size").assertIsDisplayed()
        compose.onNodeWithText("Privacy").performScrollTo().performClick()
        compose.onNodeWithText("Screen lock").assertExists()
        compose.onNodeWithText("Backup to Google Drive").assertExists()
        compose.onNodeWithText("Clear browsing data").assertExists()
        compose.onNodeWithText("About").performScrollTo().performClick()
        compose.onNodeWithText("Open-source credits").assertExists()
        compose.onNodeWithContentDescription("Back to the browser").performClick()
        compose.onNodeWithContentDescription("Address bar").assertIsDisplayed()
    }

    @Test
    fun settingsSwitchChangesTheSetting() {
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Settings").performClick()
        assertTrue(onActivity { it.settings.ads.value })
        compose.onNodeWithText("Block ads").performClick()
        assertFalse(onActivity { it.settings.ads.value })
        compose.onNodeWithText("Browsing").performScrollTo().performClick()
        compose.onNodeWithText("Startpage").performScrollTo().performClick()
        assertEquals("startpage", onActivity { it.settings.searchEngine })
    }

    @Test
    fun firstRunWalkthroughAsksOnceAndStartsSimple() {
        launch(setupDone = false)
        compose.onNodeWithText("Welcome to paindu").assertIsDisplayed()
        shot("20-setup-welcome")
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithText("Open links with paindu?").assertIsDisplayed()
        compose.onNodeWithText("Make paindu my default browser").assertExists()
        shot("21-setup-default-browser")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Lock paindu?").assertIsDisplayed()
        shot("22-setup-lock")
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Add anything?").assertIsDisplayed()
        shot("23-setup-plugins")
        compose.onNodeWithText("Use History").performScrollTo().performClick()
        compose.onNodeWithText("Finish").performClick()

        // Setup is over: the browser is showing, only the chosen plugin is on, nothing was forced.
        compose.onNodeWithContentDescription("Address bar").assertIsDisplayed()
        assertTrue(onActivity { it.settings.setupDone.value })
        assertTrue(onActivity { it.settings.pluginOn("history") })
        assertFalse(onActivity { it.settings.pluginOn("whush") })
        assertFalse(onActivity { it.settings.pluginOn("care") })
        assertFalse(onActivity { it.settings.appLock.value })
        compose.onNodeWithContentDescription("Open: Device care").assertDoesNotExist()
    }

    @Test
    fun skippingSetupLeavesEverythingOff() {
        launch(setupDone = false)
        compose.onNodeWithText("Skip setup").performClick()
        compose.onNodeWithContentDescription("Address bar").assertIsDisplayed()
        assertFalse(onActivity { it.settings.pluginOn("history") || it.settings.pluginOn("whush") || it.settings.pluginOn("care") })
        // No history plugin: visiting a page must leave no trace.
        loadPage("https://example.com/", "<html><head><title>visited</title></head><body>hello</body></html>")
        waitFor("the page") { it.browser.current!!.title == "visited" }
        Thread.sleep(800)
        assertTrue(onActivity { it.settings.history.isEmpty() })
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("History").assertDoesNotExist()
        compose.onNodeWithText("New whush tab").assertDoesNotExist()
        compose.onNodeWithText("Device care").assertDoesNotExist()
        compose.onNodeWithText("Plugins").assertExists()
    }

    @Test
    fun screenLockNeedsAPhoneScreenLock() {
        // The emulator has no PIN set, so paindu must refuse to lock rather than lock the user out.
        assertFalse(onActivity { it.canUseScreenLock() })
        assertFalse(onActivity { it.setAppLock(true) })
        assertFalse(onActivity { it.settings.appLock.value })
        assertFalse(onActivity { it.locked })
    }

    @Test
    fun whushTabsKeepTheirOwnCookiesAndForgetThem() {
        assumeTrue("this emulator's web engine is too old for whush", onActivity { it.browser.whushSupported })
        val cookies = android.webkit.CookieManager.getInstance()
        fun normalCookies(): String = onActivity { cookies.getCookie("https://example.com/") } ?: ""

        // A normal tab sets a cookie.
        loadPage("https://example.com/", "<html><head><title>normal</title></head><body><script>document.cookie='seen=normal; path=/; max-age=600';document.title='normal-set'</script></body></html>")
        waitFor("the normal tab's cookie") { it.browser.current!!.title == "normal-set" }
        assertTrue("normal tab's cookie should be stored, got '${normalCookies()}'", normalCookies().contains("seen=normal"))

        // A whush tab on the same site can't see it, and its own cookie stays out of the normal store.
        onActivity { it.browser.addWhushTab() }
        assertTrue("the new tab should be a whush tab", onActivity { it.browser.current!!.whush })
        loadPage("https://example.com/", "<html><head><title>w</title></head><body><script>var had=document.cookie.indexOf('seen=normal')>=0;document.cookie='seen=whush; path=/; max-age=600';document.title=had?'leaked':'separate'</script></body></html>")
        waitFor("the whush tab to run") { it.browser.current!!.title == "separate" || it.browser.current!!.title == "leaked" }
        assertEquals("a whush tab must not see normal cookies", "separate", onActivity { it.browser.current!!.title })
        Thread.sleep(500)
        assertFalse("a whush cookie must not reach the normal store, got '${normalCookies()}'", normalCookies().contains("seen=whush"))

        // whush leaves no history and isn't saved for next time.
        assertTrue("whush pages must not be in history", onActivity { a -> a.settings.history.none { it.title == "separate" } })
        onActivity { it.browser.saveSession() }
        val saved = context.getSharedPreferences("paindu", Context.MODE_PRIVATE).getString("session", "") ?: ""
        assertEquals("only the normal tab is saved: $saved", 1, org.json.JSONArray(saved).length())

        // Closing the last whush tab deletes the whole compartment.
        onActivity { it.browser.closeWhushTabs() }
        Thread.sleep(4000)
        val left = onActivity { androidx.webkit.ProfileStore.getInstance().allProfileNames.toString() }
        val note = onActivity { it.browser.whushCleanupNote }
        assertFalse("the whush profile should be deleted; profiles=$left note=$note", left.contains("whush"))

        // A fresh whush tab starts with nothing.
        onActivity { it.browser.addWhushTab() }
        loadPage("https://example.com/", "<html><head><title>w2</title></head><body><script>document.title=document.cookie.indexOf('seen=whush')>=0?'remembered':'forgotten'</script></body></html>")
        waitFor("the second whush tab") { it.browser.current!!.title == "forgotten" || it.browser.current!!.title == "remembered" }
        assertEquals("a new whush tab must start empty", "forgotten", onActivity { it.browser.current!!.title })
    }

    @Test
    fun backgroundTabsSleepUnderMemoryPressureAndWake() {
        loadPage("https://example.com/one", "<html><head><title>one</title></head><body>one</body></html>")
        waitFor("tab one") { it.browser.current!!.title == "one" }
        val first = onActivity { it.browser.current!! }
        onActivity { it.browser.addTab() }
        loadPage("https://example.com/two", "<html><head><title>two</title></head><body>two</body></html>")
        waitFor("tab two") { it.browser.current!!.title == "two" }
        assertFalse(first.asleep)

        // Android reports critical memory: every background tab sleeps, the visible one doesn't.
        onActivity { it.browser.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) }
        assertTrue(first.asleep)
        assertTrue(onActivity { first.view == null })
        assertFalse(onActivity { it.browser.current!!.asleep })
        assertEquals("https://example.com/one", first.url)

        // Going back to it wakes it.
        onActivity { it.browser.select(first) }
        waitFor("tab one to wake") { !first.asleep && first.view != null }
    }

    @Test
    fun restoredTabsStartAsleepExceptTheOneShown() {
        launch(setupDone = true, session = "[\"https://example.com/a\",\"https://example.org/b\",\"https://example.net/c\"]")
        assertEquals(3, onActivity { it.browser.tabs.size })
        waitFor("the shown tab to wake") { !it.browser.tabs[0].asleep }
        assertTrue(onActivity { it.browser.tabs[1].asleep && it.browser.tabs[1].view == null })
        assertTrue(onActivity { it.browser.tabs[2].asleep && it.browser.tabs[2].view == null })
    }

    @Test
    fun historyPluginRemembersAndForgets() {
        loadPage("https://example.com/remember", "<html><head><title>remember me</title></head><body>x</body></html>")
        waitFor("history entry") { a -> a.settings.history.any { it.title == "remember me" } }
        // Switching the plugin off deletes what it kept.
        onActivity { it.browser.setPlugin("history", false) }
        assertTrue(onActivity { it.settings.history.isEmpty() })
    }

    /** Saves a half-size screenshot where the build collects extra test output. */
    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val dir = File(path)
        dir.mkdirs()
        val full = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val half = Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true)
        File(dir, "$name.jpg").outputStream().use { half.compress(Bitmap.CompressFormat.JPEG, 70, it) }
    }

    private fun darkTheme(on: Boolean) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd uimode night " + if (on) "yes" else "no").close()
        Thread.sleep(2000)
    }

    /** Not a check: a walk through the app that records what each screen looks like. */
    @Test
    fun recordScreens() {
        try {
            shot("01-home")
            compose.onNodeWithContentDescription("Open: Plugins").performScrollTo().performClick()
            shot("16-plugins-available")
            compose.onNodeWithText("Planned").performClick()
            shot("17-plugins-planned")
            compose.onNodeWithContentDescription("Back to the browser").performClick()
            compose.onNodeWithContentDescription("Open: Device care").performScrollTo().performClick()
            shot("02-care-security")
            compose.onNodeWithText("Storage").performClick()
            shot("03-care-storage")
            compose.onNodeWithText("Health").performClick()
            shot("04-care-health")
            compose.onNodeWithContentDescription("Back to the browser").performClick()

            compose.onNodeWithContentDescription("Menu").performClick()
            shot("05-menu")
            compose.onNodeWithText("Settings").performClick()
            shot("06-settings-shield")
            compose.onNodeWithText("Browsing").performScrollTo().performClick()
            shot("07-settings-browsing")
            compose.onNodeWithText("Reading").performScrollTo().performClick()
            shot("08-settings-reading")
            compose.onNodeWithText("Privacy").performScrollTo().performClick()
            shot("09-settings-privacy")
            compose.onNodeWithText("About").performScrollTo().performClick()
            shot("10-settings-about")

            darkTheme(true)
            shot("11-dark-settings-about")
            compose.onNodeWithText("Shield").performScrollTo().performClick()
            shot("12-dark-settings-shield")
            compose.onNodeWithContentDescription("Back to the browser").performClick()
            shot("13-dark-home")
            darkTheme(false)

            waitFor("block lists loaded") { it.shield.totalDomains > 0 }
            onActivity { it.browser.load(it.browser.current!!, "https://malware-site.test/payload") }
            waitFor("the warning page") { it.browser.current!!.title == "Blocked: dangerous site" }
            shot("14-warning")

            loadPage(
                "https://example.com/",
                "<html><head><title>A page</title><meta name='viewport' content='width=device-width'></head>" +
                    "<body style='font:18px sans-serif;padding:16px'><h1>A page</h1><p>Some text on a page.</p>" +
                    "<img src='https://ad.doubleclick.net/pixel.gif'></body></html>",
            )
            waitFor("the ad request to be blocked") { it.browser.current!!.title == "A page" && it.browser.current!!.blocked == 1 }
            compose.onNode(hasContentDescription("Shield.", substring = true)).performClick()
            shot("15-shield-sheet")
        } finally {
            darkTheme(false)
        }
    }

    @Test
    fun blocksThirdPartyAdRequestsOnly() {
        loadPage(
            "https://example.com/",
            "<html><head><title>ads</title></head><body>" +
                "<img src='https://ad.doubleclick.net/pixel.gif'>" +
                "<img src='https://example.com/own-image.gif'></body></html>",
        )
        waitFor("exactly the ad request to be blocked") { it.browser.current!!.blocked == 1 }
        Thread.sleep(1500)
        assertEquals(1, onActivity { it.browser.current!!.blocked })
    }

    @Test
    fun pausingASiteStopsBlocking() {
        onActivity { it.settings.togglePaused("example.com") }
        loadPage(
            "https://example.com/",
            "<html><head><title>paused</title></head><body>" +
                "<img src='https://ad.doubleclick.net/pixel.gif'></body></html>",
        )
        waitFor("page to load") { it.browser.current!!.title == "paused" }
        Thread.sleep(2500)
        assertEquals(0, onActivity { it.browser.current!!.blocked })
    }

    @Test
    fun showsWarningForMalwareSite() {
        waitFor("block lists loaded") { it.shield.totalDomains > 0 }
        onActivity { it.browser.load(it.browser.current!!, "https://malware-site.test/payload") }
        waitFor("the warning page") { it.browser.current!!.title == "Blocked: dangerous site" }
        assertTrue(onActivity { it.settings.history.isEmpty() })

        // The count of blocked requests must start again on the next page, not carry over.
        Thread.sleep(1500)
        assertEquals(0, onActivity { it.browser.current!!.blocked })
        loadPage(
            "https://example.com/",
            "<html><head><title>next</title></head><body><img src='https://ad.doubleclick.net/pixel.gif'></body></html>",
        )
        waitFor("the next page") { it.browser.current!!.title == "next" }
        waitFor("one blocked request on the next page") { it.browser.current!!.blocked == 1 }
        Thread.sleep(1500)
        assertEquals(1, onActivity { it.browser.current!!.blocked })
    }

    @Test
    fun rejectsCookieBannerAndNeverAccepts() {
        loadPage(
            "https://example.org/",
            "<html><head><title>banner</title></head><body><div id='cookie-consent-banner'>" +
                "<p>We use cookies.</p>" +
                "<button onclick=\"document.title='accepted'\">Accept all</button>" +
                "<button onclick=\"document.title='rejected'\">Reject all</button>" +
                "</div></body></html>",
        )
        waitFor("the Reject button to be pressed") { it.browser.current!!.title == "rejected" }
    }

    @Test
    fun popupWithoutATapIsNotOpened() {
        loadPage(
            "https://example.org/",
            "<html><head><title>popup</title></head><body><script>" +
                "window.open('https://example.org/one');window.open('https://example.org/two');" +
                "document.title='ran';</script></body></html>",
        )
        waitFor("the page script to run") { it.browser.current!!.title == "ran" }
        Thread.sleep(1500)
        assertEquals(1, onActivity { it.browser.tabs.size })
    }

    @Test
    fun readerViewExtractsTheArticle() {
        val paragraph = "<p>" + "The mechanic checked every part of the engine before closing the hood. ".repeat(12) + "</p>"
        loadPage(
            "https://example.net/story",
            "<html><head><title>A story</title></head><body><nav>Home | About</nav><article><h1>A story</h1>" +
                paragraph.repeat(6) + "</article><footer>Footer</footer></body></html>",
        )
        waitFor("the article to load") { it.browser.current!!.title == "A story" }
        onActivity { it.browser.toggleReader(it.browser.current!!) }
        waitFor("reader view") { it.browser.current!!.reader }
    }

    @Test
    fun deviceCareChecksRunWithoutSpecialAccess() {
        val health = Care.health(context)
        assertTrue(health.ramTotal > 0)
        assertTrue(health.storageTotal > 0)
        assertEquals(8, Care.powerfulAccess(context).size)
        assertFalse(Care.hasUsageAccess(context))
        Care.appSizes(context) // must not crash without usage access
        val nothingListed = HashDb(longArrayOf(1L))
        assertTrue(Care.scanApps(context, nothingListed) { _, _ -> }.isEmpty())
    }
}
