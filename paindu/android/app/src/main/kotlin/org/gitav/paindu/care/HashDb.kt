package org.gitav.paindu.care

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Arrays

/**
 * A set of known-bad file fingerprints.
 *
 * Each entry is the first 8 bytes of a file's SHA-256. That keeps a million
 * signatures in about 8 MB, and the chance of a clean file sharing a prefix with
 * a listed one is negligible (about one in 10 trillion per file checked).
 *
 * File format: the ASCII magic "PAINDUH1", a big-endian 64-bit entry count, then
 * that many big-endian 64-bit prefixes. Built by android/tools/build_signatures.py.
 */
class HashDb(prefixes: LongArray) {
    private val sorted: LongArray = prefixes.copyOf().also { Arrays.sort(it) }

    val size: Int get() = sorted.size

    fun contains(sha256: ByteArray): Boolean = Arrays.binarySearch(sorted, prefixOf(sha256)) >= 0

    companion object {
        const val MAGIC = "PAINDUH1"
        private const val MAX_ENTRIES = 20_000_000L

        fun prefixOf(sha256: ByteArray): Long {
            require(sha256.size >= 8) { "hash too short" }
            var v = 0L
            for (i in 0 until 8) v = (v shl 8) or (sha256[i].toLong() and 0xff)
            return v
        }

        @Throws(IOException::class)
        fun read(input: InputStream): HashDb {
            val data = DataInputStream(BufferedInputStream(input, 1 shl 16))
            val magic = ByteArray(MAGIC.length)
            data.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != MAGIC) throw IOException("Not a paindu signature file")
            val count = data.readLong()
            if (count < 0 || count > MAX_ENTRIES) throw IOException("Unreasonable signature count: $count")
            val out = LongArray(count.toInt())
            for (i in out.indices) out[i] = data.readLong()
            return HashDb(out)
        }

        /** SHA-256 of everything in the stream. The caller closes the stream. */
        @Throws(IOException::class)
        fun sha256(input: InputStream): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
            return md.digest()
        }

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
