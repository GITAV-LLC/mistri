package org.gitav.paindu

import org.gitav.paindu.shield.FilterLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListsTest {
    @Test
    fun parsesAbpDomainRulesOnly() {
        val text = """
            [Adblock Plus 2.0]
            ! comment
            ||ads.example.com^
            ||Tracker.Example.net^${'$'}third-party
            ||scoped.example.org^${'$'}domain=foo.com
            ||pathrule.example.com/banner^
            @@||allowed.example.com^
            example.com##.ad
            ||popup.example.com^${'$'}popup
            ||multi.example.com^${'$'}script,image
        """.trimIndent()
        val got = FilterLists.parse(text.lineSequence(), "abp")
        assertEquals(setOf("ads.example.com", "tracker.example.net", "multi.example.com"), got)
    }

    @Test
    fun parsesHostsFiles() {
        val text = """
            # hosts
            127.0.0.1 localhost
            127.0.0.1 bad.example.com
            0.0.0.0 Worse.Example.com.
            10.0.0.1 not-a-block.example.com
            0.0.0.0 not_valid
        """.trimIndent()
        val got = FilterLists.parse(text.lineSequence(), "hosts")
        assertEquals(setOf("bad.example.com", "worse.example.com"), got)
    }

    @Test
    fun collapseDropsCoveredSubdomains() {
        val got = FilterLists.collapse(setOf("example.com", "a.example.com", "b.a.example.com", "other.net"))
        assertEquals(listOf("example.com", "other.net"), got)
    }

    @Test
    fun hostInSetMatchesParents() {
        val set = setOf("doubleclick.net")
        assertTrue(FilterLists.hostInSet("ad.g.doubleclick.net", set))
        assertTrue(FilterLists.hostInSet("DoubleClick.net", set))
        assertFalse(FilterLists.hostInSet("notdoubleclick.net", set))
        assertFalse(FilterLists.hostInSet(null, set))
    }

    @Test
    fun thirdPartyDetection() {
        assertEquals("example.co.uk", FilterLists.baseDomain("news.example.co.uk"))
        assertEquals("example.com", FilterLists.baseDomain("a.b.example.com"))
        assertEquals("192.168.1.10", FilterLists.baseDomain("192.168.1.10"))
        assertTrue(FilterLists.isThirdParty("ads.tracker.net", "www.example.com"))
        assertFalse(FilterLists.isThirdParty("static.example.com", "www.example.com"))
        assertFalse(FilterLists.isThirdParty("a.example.co.uk", "b.example.co.uk"))
        assertTrue(FilterLists.isThirdParty("other.co.uk", "example.co.uk"))
        assertFalse(FilterLists.isThirdParty("anything.net", ""))
    }

    @Test
    fun cleanHost() {
        assertEquals("example.com", FilterLists.cleanHost(" *.Example.com:8080. ".trim()))
    }
}
