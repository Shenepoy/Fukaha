package app.fukaha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class EmbedCatalog(
    private val platforms: Map<String, EmbedPlatform>,
) {
    // A catalog is immutable once parsed, so everything derived from it is computed
    // once. The settings list asks for these per platform on every recomposition.
    private val orderedKeys: List<String> by lazy {
        val rank = PLATFORM_ORDER.withIndex().associate { it.value to it.index }
        platforms.keys.sortedWith(
            compareBy<String> { rank[it] ?: Int.MAX_VALUE }.thenBy { it },
        )
    }

    private val activeServicesByPlatform: Map<String, List<EmbedService>> by lazy {
        platforms.mapValues { (_, platform) -> platform.services.filterNot { it.isBroken } }
    }

    private val defaultServiceByPlatform: Map<String, EmbedService> by lazy {
        platforms.keys.mapNotNull { key ->
            defaultService(key, activeServices(key))?.let { key to it }
        }.toMap()
    }

    fun platformKeys(): List<String> = orderedKeys

    fun platform(key: String): EmbedPlatform? = platforms[key]

    fun activeServices(platformKey: String): List<EmbedService> =
        activeServicesByPlatform[platformKey].orEmpty()

    fun detectPlatformKey(url: String): String? {
        val host = UrlCleaner.hostOf(url) ?: return null
        return hostToPlatform[host] ?: hostToPlatform.entries
            .firstOrNull { (pattern, _) -> host == pattern || host.endsWith(".$pattern") }
            ?.value
    }

    fun detect(url: String): DetectedLink {
        val cleaned = UrlCleaner.clean(url)
        val key = detectPlatformKey(cleaned)
        val name = key?.let { platforms[it]?.name ?: it.replaceFirstChar { c -> c.uppercase() } }
        return DetectedLink(
            originalUrl = url,
            cleanedUrl = cleaned,
            platformKey = key,
            platformName = name,
        )
    }

    fun rewriteToEmbed(
        url: String,
        preferredFixerHost: String? = null,
        health: Map<String, EmbedHealthStatus> = emptyMap(),
    ): String? {
        val cleaned = UrlCleaner.clean(url)
        val key = detectPlatformKey(cleaned) ?: return null
        val service = effectiveService(key, preferredFixerHost, health) ?: return null
        return replaceHost(cleaned, service.normalizedHost())
    }

    /**
     * Explicit choices stay selected. With no choice, health guides the automatic
     * selection. Mirrors [rewriteToEmbed] so the
     * settings list can show what sharing will actually use.
     */
    fun effectiveService(
        platformKey: String,
        preferredFixerHost: String? = null,
        health: Map<String, EmbedHealthStatus> = emptyMap(),
    ): EmbedService? {
        val services = activeServices(platformKey)
        if (services.isEmpty()) return null
        return EmbedHealthPolicy.pickService(
            services = services,
            preferred = serviceForHost(platformKey, preferredFixerHost),
            default = defaultServiceByPlatform[platformKey],
            health = health,
        )
    }

    /** Service owning [host], matching its alternate hosts and name too. */
    fun serviceForHost(platformKey: String, host: String?): EmbedService? {
        val normalized = host?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        return activeServices(platformKey).firstOrNull {
            EmbedHealthKeys.normalize(it.normalizedHost()) == EmbedHealthKeys.normalize(normalized) ||
                it.alternateHosts.any { alt ->
                    EmbedHealthKeys.normalize(alt) == EmbedHealthKeys.normalize(normalized)
                } ||
                it.name.equals(normalized, ignoreCase = true)
        }
    }

    fun defaultFixerHost(platformKey: String): String? =
        defaultServiceByPlatform[platformKey]?.normalizedHost()

    private fun defaultService(platformKey: String, services: List<EmbedService>): EmbedService? {
        val preferredHost = DEFAULT_FIXERS[platformKey] ?: return services.firstOrNull()
        return services.firstOrNull {
            it.normalizedHost().equals(preferredHost, ignoreCase = true)
        } ?: services.firstOrNull()
    }

    private fun replaceHost(url: String, newHostWithScheme: String): String {
        val schemeSplit = url.indexOf("://")
        if (schemeSplit < 0) return url
        val rest = url.substring(schemeSplit + 3)
        val pathIdx = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
        val pathAndAfter = rest.substring(pathIdx)

        val target = newHostWithScheme.trim().trimEnd('/')
        return if (target.contains("://")) {
            "$target$pathAndAfter"
        } else {
            "https://$target$pathAndAfter"
        }
    }

    companion object {
        /** Most-used networks first; unknown keys sort after these, A–Z. */
        val PLATFORM_ORDER: List<String> = listOf(
            "youtube",
            "instagram",
            "tiktok",
            "facebook",
            "x",
            "reddit",
            "snapchat",
            "spotify",
            "pinterest",
            "threads",
            "twitch",
            "tumblr",
            "bluesky",
            "imgur",
            "roblox",
            "pixiv",
            "deviantart",
            "bilibili",
            "weibo",
        )

        val DEFAULT_FIXERS: Map<String, String> = mapOf(
            "bilibili" to "https://vxbilibili.com",
            "bluesky" to "https://bskx.app",
            "deviantart" to "https://fixdeviantart.com",
            "facebook" to "https://facebed.com",
            "imgur" to "https://imgurez.com",
            "instagram" to "https://ddinstagram.com",
            "pinterest" to "https://pinterestez.com",
            "pixiv" to "https://phixiv.net",
            "reddit" to "https://rxddit.com",
            "roblox" to "https://fixroblox.com",
            "snapchat" to "https://snapchatez.com",
            "spotify" to "https://fxspotify.com",
            "threads" to "https://fixthreads.net",
            "tiktok" to "https://tnktok.com",
            "tumblr" to "https://tpmblr.com",
            "twitch" to "https://fxtwitch.seria.moe",
            "weibo" to "https://weiboez.com",
            "x" to "https://fixvx.com",
            "youtube" to "https://koutube.com",
        )

        private val hostToPlatform: Map<String, String> = mapOf(
            "b23.tv" to "bilibili",
            "bilibili.com" to "bilibili",
            "bsky.app" to "bluesky",
            "deviantart.com" to "deviantart",
            "facebook.com" to "facebook",
            "fb.com" to "facebook",
            "fb.watch" to "facebook",
            "imgur.com" to "imgur",
            "instagram.com" to "instagram",
            "instagr.am" to "instagram",
            "pin.it" to "pinterest",
            "pinterest.com" to "pinterest",
            "pixiv.net" to "pixiv",
            "reddit.com" to "reddit",
            "old.reddit.com" to "reddit",
            "new.reddit.com" to "reddit",
            "redd.it" to "reddit",
            "roblox.com" to "roblox",
            "snapchat.com" to "snapchat",
            "spotify.com" to "spotify",
            "threads.net" to "threads",
            "threads.com" to "threads",
            "tiktok.com" to "tiktok",
            "tumblr.com" to "tumblr",
            "twitch.tv" to "twitch",
            "twitter.com" to "x",
            "mobile.twitter.com" to "x",
            "weibo.cn" to "weibo",
            "weibo.com" to "weibo",
            "x.com" to "x",
            "mobile.x.com" to "x",
            "youtube.com" to "youtube",
            "youtu.be" to "youtube",
        )

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun fromJson(raw: String): EmbedCatalog {
            val root = json.parseToJsonElement(raw) as JsonObject
            val platforms = root.mapValues { (_, value) ->
                json.decodeFromJsonElement<EmbedPlatform>(value)
            }
            return EmbedCatalog(platforms)
        }

        /** Immutable, so every caller can share one parse of the bundled catalog. */
        private val bundledCatalog: EmbedCatalog by lazy { fromJson(ServicesJson.RAW) }

        fun bundled(): EmbedCatalog = bundledCatalog
    }
}
