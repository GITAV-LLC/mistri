package org.gitav.paindu

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** A switch that remembers its position. */
class BoolPref(private val prefs: SharedPreferences, private val key: String, default: Boolean) {
    private val state = mutableStateOf(prefs.getBoolean(key, default))
    var value: Boolean
        get() = state.value
        set(v) {
            state.value = v
            prefs.edit().putBoolean(key, v).apply()
        }
}

data class Link(val title: String, val url: String, val time: Long = 0L)

data class SearchEngine(val id: String, val name: String, val prefix: String)

val SEARCH_ENGINES = listOf(
    SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    SearchEngine("google", "Google", "https://www.google.com/search?q="),
    SearchEngine("bing", "Bing", "https://www.bing.com/search?q="),
    SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q="),
    SearchEngine("startpage", "Startpage", "https://www.startpage.com/do/search?q="),
)

/** Everything paindu remembers. It all lives in the app's private storage on this phone. */
class Settings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("paindu", Context.MODE_PRIVATE)

    // Shield
    val ads = BoolPref(prefs, "ads", true)
    val trackers = BoolPref(prefs, "trackers", true)
    val malware = BoolPref(prefs, "malware", true)
    val popups = BoolPref(prefs, "popups", true)
    val cookies = BoolPref(prefs, "cookies", true)
    val cosmetic = BoolPref(prefs, "cosmetic", true)
    val autoUpdate = BoolPref(prefs, "autoUpdate", true)
    val safeBrowsing = BoolPref(prefs, "safeBrowsing", true)
    val restoreTabs = BoolPref(prefs, "restoreTabs", true)

    var searchEngine by mutableStateOf(prefs.getString("searchEngine", "duckduckgo") ?: "duckduckgo")
        private set

    fun chooseSearchEngine(id: String) {
        searchEngine = id
        prefs.edit().putString("searchEngine", id).apply()
    }

    fun searchPrefix(): String =
        (SEARCH_ENGINES.firstOrNull { it.id == searchEngine } ?: SEARCH_ENGINES[0]).prefix

    /** Page text size in percent, applied on top of the phone's own font size setting. */
    var textScale by mutableIntStateOf(prefs.getInt("textScale", 100))
        private set

    fun chooseTextScale(percent: Int) {
        textScale = percent
        prefs.edit().putInt("textScale", percent).apply()
    }

    /** Sites where the user has paused protection. */
    var paused by mutableStateOf(prefs.getStringSet("paused", null)?.toSet() ?: emptySet())
        private set

    /** Returns true when the site is now paused. */
    fun togglePaused(host: String): Boolean {
        if (host.isEmpty()) return false
        val next = if (host in paused) paused - host else paused + host
        paused = next
        prefs.edit().putStringSet("paused", next).apply()
        return host in next
    }

    val bookmarks = mutableStateListOf<Link>()
    val history = mutableStateListOf<Link>()

    init {
        bookmarks.addAll(readLinks("bookmarks"))
        history.addAll(readLinks("history"))
    }

    fun isBookmarked(url: String): Boolean = bookmarks.any { it.url == url }

    fun toggleBookmark(title: String, url: String) {
        if (url.isEmpty()) return
        if (!bookmarks.removeAll { it.url == url }) bookmarks.add(0, Link(title.ifBlank { url }, url))
        writeLinks("bookmarks", bookmarks)
    }

    fun addHistory(title: String, url: String) {
        if (history.firstOrNull()?.url == url) return
        history.add(0, Link(title.ifBlank { url }, url, System.currentTimeMillis()))
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        writeLinks("history", history)
    }

    fun clearHistory() {
        history.clear()
        writeLinks("history", history)
    }

    private fun readLinks(key: String): List<Link> {
        val out = ArrayList<Link>()
        try {
            val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Link(o.optString("t"), o.optString("u"), o.optLong("d")))
            }
        } catch (e: Exception) {
            // A damaged list is treated as empty rather than crashing the app.
        }
        return out
    }

    private fun writeLinks(key: String, links: List<Link>) {
        val arr = JSONArray()
        for (l in links) arr.put(JSONObject().put("t", l.title).put("u", l.url).put("d", l.time))
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        const val MAX_HISTORY = 500
    }
}
