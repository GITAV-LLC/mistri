package org.gitav.paindu.shield

/**
 * Open-source filter lists and a small parser that turns them into domain sets.
 * A Kotlin port of extension/lists.js, kept deliberately in step with it.
 *
 * Only whole-domain rules are used (`||example.com^` and hosts-file lines). That
 * covers the bulk of ad, tracker and malware hosts, stays fast, and avoids the
 * site breakage that complex cosmetic or path rules can cause.
 */
data class FilterList(
    val id: String,
    val category: String,
    val name: String,
    val url: String,
    val format: String,
    val license: String,
    val mirrors: List<String> = emptyList(),
)

object FilterLists {
    const val ADS = "ads"
    const val TRACKERS = "trackers"
    const val MALWARE = "malware"

    val LISTS = listOf(
        FilterList(
            "easylist", ADS, "EasyList",
            "https://easylist.to/easylist/easylist.txt", "abp", "GPLv3 / CC BY-SA 3.0",
            listOf("https://raw.githubusercontent.com/easylist/easylist/gh-pages/easylist.txt"),
        ),
        FilterList(
            "easyprivacy", TRACKERS, "EasyPrivacy",
            "https://easylist.to/easylist/easyprivacy.txt", "abp", "GPLv3 / CC BY-SA 3.0",
            listOf("https://raw.githubusercontent.com/easylist/easylist/gh-pages/easyprivacy.txt"),
        ),
        FilterList(
            "pgl", ADS, "Peter Lowe's Blocklist",
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            "hosts", "Free to use (see pgl.yoyo.org)",
        ),
        FilterList(
            "urlhaus", MALWARE, "URLhaus malicious hosts (abuse.ch)",
            "https://urlhaus.abuse.ch/downloads/hostfile/", "hosts", "CC0",
        ),
        FilterList(
            "ubo-badware", MALWARE, "uBlock filters – Badware risks",
            "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
            "abp", "GPLv3",
        ),
    )

    private val DOMAIN_RE =
        Regex("^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,62}$")
    private val ABP_RE = Regex("^\\|\\|([a-z0-9.-]+)\\^(?:\\$(.*))?$", RegexOption.IGNORE_CASE)
    private val HOSTS_IP_RE = Regex("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1?|::)$")
    private val SPACE_RE = Regex("\\s+")

    // ABP options that don't narrow which pages a rule applies to; anything else
    // (domain=, ~third-party, redirect=, removeparam=, csp=, popup, ...) is skipped.
    private val SAFE_OPTS = setOf(
        "third-party", "3p", "all", "doc", "document", "script", "image", "xhr",
        "xmlhttprequest", "subdocument", "frame", "media", "object", "ping", "websocket", "other",
        "stylesheet", "css", "font", "important",
    )
    private val SKIP_HOSTS = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "0.0.0.0",
    )

    fun isDomain(host: String): Boolean = DOMAIN_RE.matches(host)

    fun parse(lines: Sequence<String>, format: String): MutableSet<String> {
        val out = HashSet<String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '#' || line[0] == '[') continue
            if (format == "hosts") {
                val parts = line.split(SPACE_RE)
                if (parts.size < 2) continue
                if (!HOSTS_IP_RE.matches(parts[0])) continue
                val host = parts[1].lowercase().removeSuffix(".")
                if (host !in SKIP_HOSTS && isDomain(host)) out.add(host)
                continue
            }
            // ABP / uBO syntax
            if (line.startsWith("@@") || line.contains("##") || line.contains("#@#") ||
                line.contains("#?#") || line.contains("#$#") || line.contains("#%#")
            ) continue
            val m = ABP_RE.matchEntire(line) ?: continue
            val opts = m.groups[2]?.value
            if (!opts.isNullOrEmpty()) {
                if (!opts.lowercase().split(",").all { it in SAFE_OPTS }) continue
            }
            val host = m.groupValues[1].lowercase()
            if (isDomain(host)) out.add(host)
        }
        return out
    }

    /** Drop subdomains already covered by a parent in the set. */
    fun collapse(domains: Set<String>): List<String> {
        val out = ArrayList<String>()
        for (d in domains) {
            var covered = false
            var i = d.indexOf('.')
            while (i != -1) {
                val parent = d.substring(i + 1)
                if (parent.contains('.') && parent in domains) {
                    covered = true
                    break
                }
                i = d.indexOf('.', i + 1)
            }
            if (!covered) out.add(d)
        }
        out.sort()
        return out
    }

    /** True if the host or any parent domain is in the set. */
    fun hostInSet(host: String?, set: Set<String>): Boolean {
        var h = (host ?: "").lowercase()
        while (h.isNotEmpty()) {
            if (h in set) return true
            val i = h.indexOf('.')
            if (i == -1) return false
            h = h.substring(i + 1)
        }
        return false
    }

    // Common two-part public suffixes. Without the full Public Suffix List this is an
    // approximation; when unsure it errs towards "same site", which means not blocking.
    private val TWO_PART_SUFFIX_SECOND = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go")

    /** The registrable part of a host name, e.g. `news.example.co.uk` -> `example.co.uk`. */
    fun baseDomain(host: String): String {
        val h = host.lowercase().removeSuffix(".")
        val parts = h.split('.')
        if (parts.size <= 2) return h
        if (parts.all { p -> p.isNotEmpty() && p.all { it.isDigit() } }) return h // IPv4 address
        val n = parts.size
        val twoPart = parts[n - 1].length == 2 && parts[n - 2] in TWO_PART_SUFFIX_SECOND
        val keep = if (twoPart) 3 else 2
        return parts.takeLast(minOf(keep, n)).joinToString(".")
    }

    /** True when a request to [requestHost] made by a page on [pageHost] crosses sites. */
    fun isThirdParty(requestHost: String, pageHost: String): Boolean {
        if (pageHost.isEmpty()) return false
        return baseDomain(requestHost) != baseDomain(pageHost)
    }

    fun cleanHost(h: String?): String =
        (h ?: "").lowercase().trim().removePrefix("*.").removeSuffix(".").replace(Regex(":\\d+$"), "").removeSuffix(".")
}
