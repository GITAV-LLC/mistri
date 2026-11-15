package org.gitav.mistri

import org.gitav.mistri.care.HashDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

class HashDbTest {
    private fun sha(s: String) = HashDb.sha256(ByteArrayInputStream(s.toByteArray()))

    @Test
    fun knownSha256() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashDb.hex(sha("abc")),
        )
    }

    @Test
    fun roundTripAndLookup() {
        val bad = listOf("malware-one", "malware-two", "malware-three").map { sha(it) }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(HashDb.MAGIC.toByteArray(Charsets.US_ASCII))
            out.writeLong(bad.size.toLong())
            // Deliberately unsorted, and including a prefix with the top bit set.
            bad.forEach { out.writeLong(HashDb.prefixOf(it)) }
        }
        val db = HashDb.read(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(3, db.size)
        bad.forEach { assertTrue(db.contains(it)) }
        assertFalse(db.contains(sha("a clean file")))
    }

    @Test(expected = IOException::class)
    fun rejectsOtherFiles() {
        HashDb.read(ByteArrayInputStream("<html>not a database</html>".toByteArray()))
    }
}
