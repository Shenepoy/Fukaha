package app.fukaha.web

import app.fukaha.EmbedHealthKeys
import app.fukaha.EmbedService
import kotlinx.browser.localStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Selection stays in WebSettingsStore. Stars and display order never change link rewriting. */
@Serializable
data class WebSourcePreferences(
    val favorites: Map<String, List<String>> = emptyMap(),
    val order: Map<String, List<String>> = emptyMap(),
) {
    fun isFavorite(platform: String, host: String): Boolean =
        favorites[platform].orEmpty().any { EmbedHealthKeys.normalize(it) == EmbedHealthKeys.normalize(host) }

    fun sorted(platform: String, services: List<EmbedService>): List<EmbedService> {
        val positions = order[platform].orEmpty().map(EmbedHealthKeys::normalize).distinct()
            .withIndex().associate { it.value to it.index }
        return services.sortedWith(compareBy<EmbedService>(
            { !isFavorite(platform, it.normalizedHost()) },
            { positions[EmbedHealthKeys.normalize(it.normalizedHost())] ?: Int.MAX_VALUE },
        ))
    }

    fun toggleFavorite(platform: String, host: String): WebSourcePreferences {
        val key = EmbedHealthKeys.normalize(host)
        val current = favorites[platform].orEmpty().map(EmbedHealthKeys::normalize).distinct()
        val next = if (key in current) current - key else current + key
        return copy(favorites = favorites + (platform to next))
    }

    /** Move within the favorite/non-favorite group; new catalog entries append naturally. */
    fun move(platform: String, host: String, direction: Int, services: List<EmbedService>): WebSourcePreferences {
        if (direction != -1 && direction != 1) return this
        val sorted = sorted(platform, services).map { EmbedHealthKeys.normalize(it.normalizedHost()) }.toMutableList()
        val index = sorted.indexOf(EmbedHealthKeys.normalize(host))
        val target = index + direction
        if (index < 0 || target !in sorted.indices ||
            isFavorite(platform, sorted[index]) != isFavorite(platform, sorted[target])) return this
        val other = sorted[target]
        sorted[target] = sorted[index]
        sorted[index] = other
        return copy(order = order + (platform to sorted))
    }
}

class WebSourcePreferencesStore {
    private val json = Json { ignoreUnknownKeys = true }
    fun get(): WebSourcePreferences = runCatching {
        localStorage.getItem(KEY)?.let { json.decodeFromString<WebSourcePreferences>(it) } ?: WebSourcePreferences()
    }.getOrDefault(WebSourcePreferences())

    fun save(preferences: WebSourcePreferences): Boolean = runCatching {
        localStorage.setItem(KEY, json.encodeToString(preferences))
    }.isSuccess

    private companion object { const val KEY = "fukaha_source_preferences" }
}
