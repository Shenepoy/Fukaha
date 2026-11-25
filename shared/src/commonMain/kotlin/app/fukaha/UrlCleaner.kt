package app.fukaha

import io.ktor.http.decodeURLQueryComponent

object UrlCleaner {
    private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    private val trackingParams = setOf(
        "fbclid", "twclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid",
        "igshid", "igsh", "share_id", "shareid", "mibextid",
        "cmpid", "campaign_id", "ad_id",
        "xtor", "gbraid", "wbraid", "yclid",
    )

    private val trackingPrefixes = listOf("utm_", "ga_", "pk_", "mtm_", "hsa_", "vero_", "icn")

    private val xTrackingParams = setOf("s", "t", "cxt", "cn", "ref_src", "ref_url", "refsrc", "src")
    private val facebookTrackingParams = setOf("__tn__", "sfnsn", "comment_tracking")
    private val facebookIndexedTrackingKey = Regex("""__(?:cft|xts)__\[\d+\]""")
    private val tiktokTrackingParams = setOf("_d", "_t", "_r", "u_code", "share_iid", "share_app_name")

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

        val kept = filterQuery(query, hostOf(trimmed).orEmpty(), path)
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

    private fun filterQuery(query: String?, host: String, path: String): List<Pair<String, String?>> {
        if (query.isNullOrBlank()) return emptyList()
        return query.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val eq = part.indexOf('=')
                val key = if (eq < 0) part else part.substring(0, eq)
                val value = if (eq < 0) null else part.substring(eq + 1)
                val keyLower = runCatching { key.decodeURLQueryComponent() }.getOrDefault(key).lowercase()
                if (isTracking(keyLower, host, path)) null else key to value
            }
    }

    private fun isTracking(key: String, host: String, path: String): Boolean {
        if (key in trackingParams) return true
        fun on(domain: String) = host == domain || host.endsWith(".$domain")
        return when {
            // X redirect URLs can require fields that are tracking on ordinary share links.
            on("x.com") || on("twitter.com") -> path.trimEnd('/') != "/i/redirect" && key in xTrackingParams
            on("youtube.com") || on("youtu.be") -> key == "si" || key == "feature"
            on("instagram.com") || on("instagr.am") -> key == "si" || key == "ig_rid"
            on("facebook.com") || on("fb.com") || on("fb.watch") ->
                key in facebookTrackingParams || facebookIndexedTrackingKey.matches(key)
            on("tiktok.com") -> key in tiktokTrackingParams
            on("reddit.com") -> key == "ref_source" || key == "ref_campaign"
            host == "open.spotify.com" -> key == "si"
            else -> false
        } || trackingPrefixes.any { key.startsWith(it) }
    }
}
