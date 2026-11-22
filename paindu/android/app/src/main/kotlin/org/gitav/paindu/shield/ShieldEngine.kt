package org.gitav.paindu.shield

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gitav.paindu.Settings
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class ListStat(val count: Int, val ok: Boolean, val error: String?)

/**
 * paindu's Shield: decides which requests to block and builds the script
 * that runs inside pages. The Android counterpart of extension/background.js.
 *
 * Blocking is by whole domain, from public open-source lists:
 *  - malware and scam sites are blocked everywhere, with a warning page for whole pages;
 *  - ad and tracker domains are blocked only as third parties, so visiting a site
 *    directly is never broken.
 */
class ShieldEngine(private val context: Context, private val settings: Settings) {
    @Volatile private var ads: Set<String> = emptySet()
    @Volatile private var trackers: Set<String> = emptySet()
    @Volatile private var malware: Set<String> = emptySet()
    private val allowedThisSession: MutableSet<String> = ConcurrentHashMap.newKeySet()

    var stats by mutableStateOf<Map<String, ListStat>>(emptyMap())
        private set
    var totalDomains by mutableIntStateOf(0)
        private set
    var lastUpdate by mutableLongStateOf(settings.prefs.getLong("listsUpdated", 0L))
        private set
    var updating by mutableStateOf(false)
        private set

    private val listDir = File(context.filesDir, "lists")

    // ------------------------------------------------------------ decisions

    fun isPaused(pageHost: String): Boolean = FilterLists.hostInSet(pageHost, settings.paused)

    private fun isMalware(host: String): Boolean =
        settings.malware.value && FilterLists.hostInSet(host, malware) &&
            !FilterLists.hostInSet(host, allowedThisSession)

    /** Should opening this site as a whole page show the warning instead? */
    fun shouldWarn(host: String): Boolean = isMalware(host) && !isPaused(host)

    /** Should a request made by a page be dropped? */
    fun shouldBlock(requestHost: String, pageHost: String): Boolean {
        if (isPaused(pageHost)) return false
        if (isMalware(requestHost)) return true
        if (!FilterLists.isThirdParty(requestHost, pageHost)) return false
        if (settings.ads.value && FilterLists.hostInSet(requestHost, ads)) return true
        if (settings.trackers.value && FilterLists.hostInSet(requestHost, trackers)) return true
        return false
    }

    /** Used to close pop-up tabs that land on an ad or malware domain. */
    fun isPopupTarget(host: String): Boolean =
        (settings.ads.value && FilterLists.hostInSet(host, ads)) || isMalware(host)

    /** "I understand the risk": let this site through until the app is closed. */
    fun allowOnce(host: String) {
        allowedThisSession.add(host.lowercase())
    }

    // ------------------------------------------------------------ lists

    /** Load the saved lists, or the small built-in starter lists on first run. */
    suspend fun load() = withContext(Dispatchers.IO) {
        val byCategory = HashMap<String, HashSet<String>>()
        val loadedStats = readStats().toMutableMap()
        for (list in FilterLists.LISTS) {
            val file = File(listDir, "${list.id}.txt")
            if (!file.exists()) continue
            val target = byCategory.getOrPut(list.category) { HashSet() }
            try {
                file.useLines { lines -> lines.forEach { if (it.isNotEmpty()) target.add(it) } }
            } catch (e: IOException) {
                loadedStats[list.id] = ListStat(0, false, e.message)
            }
        }
        for (cat in listOf(FilterLists.ADS, FilterLists.TRACKERS)) {
            if (byCategory[cat].isNullOrEmpty()) {
                byCategory[cat] = HashSet(readAsset("starter-$cat.txt").lines().filter { it.isNotBlank() })
            }
        }
        publish(byCategory, loadedStats)
    }

    /** True when the lists are more than a day old (or have never been downloaded). */
    fun isStale(): Boolean =
        System.currentTimeMillis() - lastUpdate > 24L * 60 * 60 * 1000 ||
            // Settings restored from a backup say "updated" although the lists themselves weren't restored.
            !File(listDir, "${FilterLists.LISTS[0].id}.txt").exists()

    /** Download every list again. Lists that fail keep their previous copy. */
    suspend fun update() {
        if (updating) return
        updating = true
        try {
            withContext(Dispatchers.IO) {
                listDir.mkdirs()
                val newStats = readStats().toMutableMap()
                for (list in FilterLists.LISTS) {
                    try {
                        val domains = FilterLists.collapse(fetch(list))
                        val tmp = File(listDir, "${list.id}.tmp")
                        tmp.bufferedWriter().use { w -> domains.forEach { w.write(it); w.newLine() } }
                        val dest = File(listDir, "${list.id}.txt")
                        if (!tmp.renameTo(dest)) {
                            tmp.copyTo(dest, overwrite = true)
                            tmp.delete()
                        }
                        newStats[list.id] = ListStat(domains.size, true, null)
                    } catch (e: Exception) {
                        val old = newStats[list.id]
                        newStats[list.id] = ListStat(old?.count ?: 0, false, e.message ?: e.javaClass.simpleName)
                    }
                }
                writeStats(newStats)
                val now = System.currentTimeMillis()
                settings.prefs.edit().putLong("listsUpdated", now).apply()
                lastUpdate = now
            }
            load()
        } finally {
            updating = false
        }
    }

    private fun publish(byCategory: Map<String, Set<String>>, newStats: Map<String, ListStat>) {
        ads = byCategory[FilterLists.ADS] ?: emptySet()
        trackers = byCategory[FilterLists.TRACKERS] ?: emptySet()
        malware = byCategory[FilterLists.MALWARE] ?: emptySet()
        totalDomains = ads.size + trackers.size + malware.size
        stats = newStats
    }

    private fun fetch(list: FilterList): Set<String> {
        var last: Exception? = null
        for (address in listOf(list.url) + list.mirrors) {
            try {
                val conn = URL(address).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "paindu-android")
                try {
                    if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                    val set = conn.inputStream.bufferedReader().useLines { FilterLists.parse(it, list.format) }
                    if (set.isNotEmpty()) return set
                    last = IOException("empty list")
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("${list.name}: ${last?.message ?: "download failed"}")
    }

    private fun readStats(): Map<String, ListStat> {
        val out = HashMap<String, ListStat>()
        try {
            val obj = JSONObject(settings.prefs.getString("listStats", "{}") ?: "{}")
            for (key in obj.keys()) {
                val o = obj.getJSONObject(key)
                out[key] = ListStat(o.optInt("count"), o.optBoolean("ok"), if (o.has("error")) o.optString("error") else null)
            }
        } catch (e: Exception) {
            // Start again from nothing if the saved stats are unreadable.
        }
        return out
    }

    private fun writeStats(stats: Map<String, ListStat>) {
        val obj = JSONObject()
        for ((id, s) in stats) {
            val o = JSONObject().put("count", s.count).put("ok", s.ok)
            if (s.error != null) o.put("error", s.error)
            obj.put(id, o)
        }
        settings.prefs.edit().putString("listStats", obj.toString()).apply()
    }

    // ------------------------------------------------------------ page script

    private val assetCache = HashMap<String, String>()

    private fun readAsset(name: String): String = synchronized(assetCache) {
        assetCache.getOrPut(name) {
            context.assets.open(name).bufferedReader().use { it.readText() }
        }
    }

    /**
     * The script injected at the start of every page: the pop-up guard, the stylesheet
     * that hides empty ad slots, and the cookie auto-reject. These are the very same
     * files the Chrome extension uses. Returns "" when all three are switched off.
     */
    fun pageScript(): String {
        val popups = settings.popups.value
        val cosmetic = settings.cosmetic.value
        val cookies = settings.cookies.value
        if (!popups && !cosmetic && !cookies) return ""
        val sb = StringBuilder()
        sb.append("(function(){\n")
        sb.append("if(window.__painduShield)return;window.__painduShield=true;\n")
        sb.append("var P=[")
        sb.append(settings.paused.filter { FilterLists.isDomain(it) }.joinToString(",") { JSONObject.quote(it) })
        sb.append("];var h=location.hostname;")
        sb.append("for(var i=0;i<P.length;i++){if(h===P[i]||h.endsWith('.'+P[i]))return;}\n")
        if (popups) sb.append(readAsset("popup_guard.js")).append("\n")
        if (cosmetic) {
            sb.append("try{var s=document.createElement('style');s.textContent=")
            sb.append(JSONObject.quote(readAsset("cosmetic.css")))
            sb.append(";(document.head||document.documentElement).appendChild(s);}catch(e){}\n")
        }
        if (cookies) {
            sb.append("var run=function(){\n").append(readAsset("cookie_reject.js")).append("\n};\n")
            sb.append("if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',run);else run();\n")
        }
        sb.append("})();")
        return sb.toString()
    }
}
