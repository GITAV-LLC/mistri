package org.gitav.paindu.care

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gitav.paindu.Settings
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The malware signature database: fingerprints of known-bad files.
 *
 * It is built once a day by a GitHub Action in GITAV's repository from
 * abuse.ch MalwareBazaar (CC0) and published as a release file. The app only ever
 * downloads that one file; nothing about the phone or its apps is sent anywhere.
 */
class Signatures(context: Context, private val settings: Settings) {
    private val file = File(context.filesDir, "signatures.bin")
    @Volatile private var db: HashDb? = null

    /** Number of signatures, or -1 when no database has been downloaded yet. */
    var count by mutableIntStateOf(-1)
        private set
    var updatedAt by mutableLongStateOf(settings.prefs.getLong("signaturesUpdated", 0L))
        private set

    /** The database, or null if there isn't one yet. Reads the file: call off the main thread. */
    fun get(): HashDb? {
        db?.let { return it }
        if (!file.exists()) return null
        try {
            val loaded: HashDb = file.inputStream().use { HashDb.read(it) }
            db = loaded
            count = loaded.size
            return loaded
        } catch (e: IOException) {
            return null
        }
    }

    /** Download the latest database. Returns null on success, or a plain-words reason. */
    suspend fun update(): String? = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "signatures.tmp")
        try {
            val conn = URL(SOURCE).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "paindu-android")
            try {
                if (conn.responseCode == 404) {
                    return@withContext "The signature database hasn't been published yet."
                }
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            val fresh = tmp.inputStream().use { HashDb.read(it) } // also checks the file is genuine
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            db = fresh
            count = fresh.size
            val now = System.currentTimeMillis()
            settings.prefs.edit().putLong("signaturesUpdated", now).apply()
            updatedAt = now
            null
        } catch (e: Exception) {
            tmp.delete()
            "Couldn't download the signature database: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    companion object {
        const val SOURCE =
            "https://github.com/GITAV-LLC/mistri/releases/download/signatures/malware-sha256.bin"
    }
}
