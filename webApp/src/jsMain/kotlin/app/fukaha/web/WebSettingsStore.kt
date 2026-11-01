package app.fukaha.web

import app.fukaha.AppLanguage
import app.fukaha.AppTheme
import app.fukaha.FukahaSettings
import app.fukaha.SettingsStore
import app.fukaha.ShareAction
import kotlinx.browser.localStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Browser half of [SettingsStore], backed by `localStorage`.
 *
 * Only the settings the web UI can honour are persisted. Media download and short-link
 * resolving are unreachable from a browser, so those fields keep their defaults and
 * `resolveShortLinks` is forced off — that way [app.fukaha.LinkProcessor] never attempts
 * a redirect fetch the browser would block anyway.
 */
class WebSettingsStore : SettingsStore {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Stored(
        val defaultAction: String = ShareAction.Ask.name,
        val preferredFixers: Map<String, String> = emptyMap(),
        val language: String = AppLanguage.System.name,
        val theme: String = AppTheme.System.name,
    )

    override suspend fun get(): FukahaSettings = read().toSettings()

    override suspend fun update(transform: (FukahaSettings) -> FukahaSettings) {
        val next = transform(get())
        localStorage.setItem(KEY, json.encodeToString(next.toStored()))
    }

    private fun read(): Stored {
        return runCatching {
            localStorage.getItem(KEY)?.let { json.decodeFromString<Stored>(it) } ?: Stored()
        }.getOrDefault(Stored())
    }

    private fun Stored.toSettings() = FukahaSettings(
        defaultAction = defaultAction.toEnum(ShareAction.entries, ShareAction.Ask),
        preferredFixers = preferredFixers,
        language = language.toEnum(AppLanguage.entries, AppLanguage.System),
        theme = theme.toEnum(AppTheme.entries, AppTheme.System),
        resolveShortLinks = false,
    ).withDownloadClamped()

    private fun FukahaSettings.toStored() = Stored(
        defaultAction = defaultAction.name,
        preferredFixers = preferredFixers,
        language = language.name,
        theme = theme.name,
    )

    private fun <T : Enum<T>> String.toEnum(values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == this } ?: fallback

    private companion object {
        const val KEY = "fukaha_settings"
    }
}
