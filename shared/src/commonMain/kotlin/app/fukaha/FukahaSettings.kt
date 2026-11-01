package app.fukaha

import io.ktor.http.URLProtocol
import io.ktor.http.Url

enum class ShareAction {
    Ask,
    Clean,
    Embed,
    Download,
}

enum class AppLanguage {
    System,
    English,
    Arabic,
    Japanese,
    SimplifiedChinese,
    Spanish,
    ;

    val isRtl: Boolean
        get() = this == Arabic

    companion object {
        /**
         * Reads current enum names and locale-tag values written by older or
         * platform-specific clients. Unknown values safely follow the system.
         */
        fun fromPersistedValue(value: String?): AppLanguage {
            if (value.isNullOrBlank()) return System
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }?.let { return it }

            val normalized = value.replace('_', '-')
            val language = normalized.substringBefore('-').lowercase()
            return when (language) {
                "en" -> English
                "ar" -> Arabic
                "ja" -> Japanese
                "zh" -> {
                    val parts = normalized.split('-')
                    val simplified = parts.any {
                        it.equals("Hans", ignoreCase = true) ||
                            it.equals("CN", ignoreCase = true) ||
                            it.equals("SG", ignoreCase = true)
                    }
                    if (simplified) SimplifiedChinese else System
                }
                "es" -> Spanish
                else -> System
            }
        }
    }
}

/** Press-and-hold duration before the theme button commits System. */
const val THEME_SYSTEM_HOLD_MS = 720

enum class AppTheme {
    System,
    Light,
    Dark,
    ;

    fun resolvesDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    /** Tap target: Light ↔ Dark. From System, pick the opposite of the current look. */
    fun toggled(systemDark: Boolean): AppTheme =
        if (resolvesDark(systemDark)) Light else Dark
}

internal fun migrateLegacyLanguageValue(stored: String?): String =
    if (stored == null || stored == AppLanguage.English.name) {
        AppLanguage.System.name
    } else {
        AppLanguage.fromPersistedValue(stored).name
    }

data class FukahaSettings(
    val defaultAction: ShareAction = ShareAction.Ask,
    val preferredFixers: Map<String, String> = emptyMap(),
    val cobaltBaseUrl: String = DEFAULT_COBALT_BASE_URL,
    val cobaltApiKey: String = "",
    val resolveShortLinks: Boolean = true,
    val language: AppLanguage = AppLanguage.System,
    val theme: AppTheme = AppTheme.System,
    val deleteCacheAfterShare: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val checkUpdatesOnLaunch: Boolean = true,
    val skippedUpdateVersion: String = "",
    val lastUpdateCheckEpochMs: Long = 0L,
) {
    /** Native transports require HTTPS; reject unusable or credential-bearing endpoints. */
    val hasValidCobaltBaseUrl: Boolean
        get() = isValidCobaltBaseUrl(cobaltBaseUrl)

    /**
     * Download requires a self-hosted Cobalt URL. If the setting is still Download
     * with an empty/invalid URL, treat it as Ask.
     */
    fun effectiveDefaultAction(): ShareAction =
        if (defaultAction == ShareAction.Download && !hasValidCobaltBaseUrl) {
            ShareAction.Ask
        } else {
            defaultAction
        }

    /** Clamp Download → Ask when Cobalt is not configured. */
    fun withDownloadClamped(): FukahaSettings =
        if (defaultAction == ShareAction.Download && !hasValidCobaltBaseUrl) {
            copy(defaultAction = ShareAction.Ask)
        } else {
            this
        }

    companion object {
        /** Empty until the user sets their own Cobalt instance. */
        const val DEFAULT_COBALT_BASE_URL = ""

        /** Former built-in default — public API is not usable by third-party apps. */
        const val LEGACY_PUBLIC_COBALT_BASE_URL = "https://api.cobalt.tools"

        fun isValidCobaltBaseUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return false
            if (trimmed.substringAfter("://").substringBefore('/').isBlank() || trimmed.any { it.isWhitespace() }) return false
            return runCatching {
                val parsed = Url(trimmed)
                parsed.protocol == URLProtocol.HTTPS && parsed.host.isNotBlank() &&
                    parsed.user.isNullOrEmpty() && parsed.password.isNullOrEmpty() &&
                    parsed.parameters.isEmpty() && parsed.fragment.isEmpty()
            }.getOrDefault(false)
        }

        fun isLegacyPublicCobaltBaseUrl(url: String): Boolean {
            val normalized = url.trim().trimEnd('/')
            return normalized.equals(LEGACY_PUBLIC_COBALT_BASE_URL, ignoreCase = true)
        }
    }
}

/** Apply just the fields edited by a UI snapshot to the latest persisted state. */
fun FukahaSettings.applyChangesFrom(before: FukahaSettings, after: FukahaSettings): FukahaSettings {
    fun <T> changed(old: T, next: T, current: T): T = if (old != next) next else current
    val fixers = preferredFixers.toMutableMap()
    (before.preferredFixers.keys + after.preferredFixers.keys).forEach { key ->
        if (before.preferredFixers[key] != after.preferredFixers[key]) {
            after.preferredFixers[key]?.let { fixers[key] = it } ?: fixers.remove(key)
        }
    }
    return copy(
        defaultAction = changed(before.defaultAction, after.defaultAction, defaultAction),
        preferredFixers = fixers,
        cobaltBaseUrl = changed(before.cobaltBaseUrl, after.cobaltBaseUrl, cobaltBaseUrl),
        cobaltApiKey = changed(before.cobaltApiKey, after.cobaltApiKey, cobaltApiKey),
        resolveShortLinks = changed(before.resolveShortLinks, after.resolveShortLinks, resolveShortLinks),
        language = changed(before.language, after.language, language),
        theme = changed(before.theme, after.theme, theme),
        deleteCacheAfterShare = changed(before.deleteCacheAfterShare, after.deleteCacheAfterShare, deleteCacheAfterShare),
        onboardingCompleted = changed(before.onboardingCompleted, after.onboardingCompleted, onboardingCompleted),
        checkUpdatesOnLaunch = changed(before.checkUpdatesOnLaunch, after.checkUpdatesOnLaunch, checkUpdatesOnLaunch),
        skippedUpdateVersion = changed(before.skippedUpdateVersion, after.skippedUpdateVersion, skippedUpdateVersion),
        lastUpdateCheckEpochMs = changed(before.lastUpdateCheckEpochMs, after.lastUpdateCheckEpochMs, lastUpdateCheckEpochMs),
    )
}
