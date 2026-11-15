package org.gitav.mistri.browser

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gitav.mistri.care.Signatures
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class DownloadRequest(
    val url: String,
    val name: String,
    val mime: String?,
    val size: Long,
    val userAgent: String,
)

sealed class DownloadResult {
    /** Saved to Downloads. [checked] is false when there is no signature database yet. */
    data class Saved(val name: String, val checked: Boolean) : DownloadResult()

    /** The file matches known malware. It is held back in private storage until the user decides. */
    data class Malware(val name: String, val mime: String?, val held: File) : DownloadResult()

    data class Failed(val reason: String) : DownloadResult()
}

/**
 * Downloads a file, fingerprints it on the way in, and only then puts it in the
 * Downloads folder. A file that matches known malware never reaches Downloads
 * unless the user explicitly chooses to keep it.
 */
class Downloader(private val context: Context, private val signatures: Signatures) {

    suspend fun fetch(request: DownloadRequest): DownloadResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val held = File(dir, "${System.currentTimeMillis()}.part")
        try {
            val conn = URL(request.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", request.userAgent)
            CookieManager.getInstance().getCookie(request.url)?.let { conn.setRequestProperty("Cookie", it) }
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                if (conn.responseCode !in 200..299) throw IOException("the site answered ${conn.responseCode}")
                conn.inputStream.use { input ->
                    held.outputStream().use { output ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            digest.update(buf, 0, n)
                            output.write(buf, 0, n)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            val db = signatures.get()
            if (db != null && db.contains(digest.digest())) {
                return@withContext DownloadResult.Malware(request.name, request.mime, held)
            }
            publish(held, request.name, request.mime)
            DownloadResult.Saved(request.name, checked = db != null)
        } catch (e: Exception) {
            held.delete()
            DownloadResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** The user chose to keep a file that matched known malware. */
    suspend fun keepAnyway(result: DownloadResult.Malware): DownloadResult = withContext(Dispatchers.IO) {
        try {
            publish(result.held, result.name, result.mime)
            DownloadResult.Saved(result.name, checked = true)
        } catch (e: Exception) {
            result.held.delete()
            DownloadResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun publish(held: File, name: String, mime: String?) {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues()
            values.put(MediaStore.Downloads.DISPLAY_NAME, name)
            if (!mime.isNullOrEmpty()) values.put(MediaStore.Downloads.MIME_TYPE, mime)
            values.put(MediaStore.Downloads.IS_PENDING, 1)
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("couldn't create the file in Downloads")
            val out = resolver.openOutputStream(uri) ?: throw IOException("couldn't write to Downloads")
            out.use { o -> held.inputStream().use { it.copyTo(o) } }
            val done = ContentValues()
            done.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, done, null, null)
        } else {
            // Before Android 10 a shared Downloads folder needs a storage permission;
            // mistri keeps the file in its own downloads folder instead.
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IOException("no storage available")
            held.copyTo(File(dir, name), overwrite = true)
        }
        held.delete()
    }
}
