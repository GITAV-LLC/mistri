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
        context.getSharedPreferences("paindu", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("autoUpdate", false).commit()
        val lists = File(context.filesDir, "lists")
        lists.mkdirs()
        File(lists, "urlhaus.txt").writeText("malware-site.test\n")
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
        onActivity { it.browser.current!!.webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", baseUrl) }
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

        // Settings: Shield, Browsing, Reading, Privacy, About
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Block ads").assertIsDisplayed()
        compose.onNodeWithText("Browsing").performScrollTo().performClick()
        compose.onNodeWithText("Search engine").assertIsDisplayed()
        compose.onNodeWithText("Reading").performScrollTo().performClick()
        compose.onNodeWithText("Page text size").assertIsDisplayed()
        compose.onNodeWithText("Privacy").performScrollTo().performClick()
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
