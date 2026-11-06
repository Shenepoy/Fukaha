package app.fukaha

import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent

/** Offline, site-owned compact formats. Never mint third-party shortener tokens. */
object OfficialShortLinks {
    // https://blog.youtube/news-and-events/make-way-for-youtube-links/
    fun shorten(cleanedUrl: String): String {
        val url = runCatching { Url(cleanedUrl) }.getOrNull() ?: return cleanedUrl
        if (url.protocol.name != "https" || !url.user.isNullOrEmpty() || !url.password.isNullOrEmpty()) return cleanedUrl
        if (url.host.lowercase() !in setOf("youtube.com", "www.youtube.com", "m.youtube.com")) return cleanedUrl
        if (url.port != 443 || url.encodedPath != "/watch") return cleanedUrl
        val ids = url.parameters.getAll("v") ?: return cleanedUrl
        val id = ids.singleOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return cleanedUrl
        // Keep functional query parameters and fragments byte-for-byte, including time/playlist context.
        val fragment = cleanedUrl.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
        val query = cleanedUrl.substringBefore('#').substringAfter('?', "")
            .split('&').filter { part ->
                part.isNotEmpty() && runCatching { part.substringBefore('=').decodeURLQueryComponent() }.getOrDefault("") != "v"
            }.joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
        val compact = "https://youtu.be/$id$query$fragment"
        return if (compact.length < cleanedUrl.length) compact else cleanedUrl
    }
}
