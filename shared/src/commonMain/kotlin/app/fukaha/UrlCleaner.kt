package app.fukaha

import io.ktor.http.decodeURLQueryComponent

object UrlCleaner {
    private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    private val trackingParams = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid",
        "igshid", "igsh", "share_id", "shareid", "mibextid",
        "cmpid", "campaign_id", "ad_id",
        "xtor", "gbraid", "wbraid", "yclid",
    )

    private val trackingPrefixes = listOf("utm_", "ga_", "pk_", "mtm_", "hsa_", "vero_", "icn")

    private val shortLinkHosts = setOf(
        "t.co", "bit.ly", "bitly.com", "tinyurl.com", "goo.gl", "ow.ly",
        "vm.tiktok.com", "vt.tiktok.com", "buff.ly", "rebrand.ly",
        "cutt.ly", "shorturl.at", "is.gd", "v.gd",
    )

    fun extractFirstUrl(text: String): String? {
        val match = urlRegex.find(text) ?: return null
        var url = match.value
        while (true) {
            val previous = url
            url = url.trimEnd('.', ',', ';', ':', '!', '?', '"', '\'')
            for ((open, close) in listOf('(' to ')', '[' to ']')) {
                while (url.endsWith(close) && url.count { it == close } > url.count { it == open }) {
                    url = url.dropLast(1)
                }
            }
            if (url == previous) return url
        }
    }

    fun isShortLink(url: String): Boolean {
        val host = hostOf(url)?.lowercase() ?: return false
        return host in shortLinkHosts || host.endsWith(".page.link")
    }

    fun clean(url: String): String {
        val trimmed = url.trim()
        val schemeSplit = trimmed.indexOf("://")
        if (schemeSplit < 0) return trimmed

        val scheme = trimmed.substring(0, schemeSplit).lowercase()
        val rest = trimmed.substring(schemeSplit + 3)
        val pathStart = rest.indexOfAny(charArrayOf('/', '?', '#'))

        val hostPort = if (pathStart < 0) rest else rest.substring(0, pathStart)
        val afterHost = if (pathStart < 0) "" else rest.substring(pathStart)

        val userInfo = hostPort.substringBeforeLast('@', "").let { if (hostPort.contains('@')) "$it@" else "" }
        val host = userInfo + hostPort.substringAfterLast('@').lowercase().removePrefix("www.")

        // Keep twitter/x as-is for cleaning; embed rewrite handles mapping.
        val (pathQuery, fragment) = splitFragment(afterHost)
        val (path, query) = splitQuery(pathQuery)

        val kept = filterQuery(query, hostOf(trimmed).orEmpty())
        val queryPart = if (kept.isEmpty()) "" else "?" + kept.joinToString("&") { (k, v) ->
            if (v == null) k else "$k=$v"
        }
        val fragmentPart = if (fragment.isNullOrEmpty()) "" else "#$fragment"

        val normalizedPath = when {
            path.isEmpty() || path == "/" -> ""
            else -> path.trimEnd('/')
        }

        return "$scheme://$host$normalizedPath$queryPart$fragmentPart"
    }

    fun hostOf(url: String): String? {
        val cleaned = url.trim()
        val schemeSplit = cleaned.indexOf("://")
        if (schemeSplit < 0) return null
        val rest = cleaned.substring(schemeSplit + 3)
        val end = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
        var host = rest.substring(0, end).substringAfterLast('@').lowercase()
        if (host.startsWith("www.")) host = host.removePrefix("www.")
        host = if (host.startsWith('[')) host.substringBefore(']') + "]" else host.substringBefore(':')
        return host.ifBlank { null }
    }

    private fun splitFragment(value: String): Pair<String, String?> {
        val idx = value.indexOf('#')
        return if (idx < 0) value to null else value.substring(0, idx) to value.substring(idx + 1)
    }

    private fun splitQuery(value: String): Pair<String, String?> {
        val idx = value.indexOf('?')
        return if (idx < 0) value to null else value.substring(0, idx) to value.substring(idx + 1)
    }

    private fun filterQuery(query: String?, host: String): List<Pair<String, String?>> {
        if (query.isNullOrBlank()) return emptyList()
        return query.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val eq = part.indexOf('=')
                val key = if (eq < 0) part else part.substring(0, eq)
                val value = if (eq < 0) null else part.substring(eq + 1)
                val keyLower = runCatching { key.decodeURLQueryComponent() }.getOrDefault(key).lowercase()
                if (isTracking(keyLower, host)) null else key to value
            }
    }

    private fun isTracking(key: String, host: String): Boolean {
        if (key in trackingParams) return true
        fun on(domain: String) = host == domain || host.endsWith(".$domain")
        if ((on("x.com") || on("twitter.com")) && key in setOf("s", "t", "ref_src", "ref_url")) return true
        if ((on("youtube.com") || on("youtu.be")) && key in setOf("si", "feature")) return true
        if (on("instagram.com") && key == "si") return true
        return trackingPrefixes.any { key.startsWith(it) }
    }
}
