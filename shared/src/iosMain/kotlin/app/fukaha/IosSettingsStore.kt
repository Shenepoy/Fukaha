package app.fukaha

import platform.Foundation.NSUserDefaults

class IosSettingsStore(
    private val defaults: NSUserDefaults = NSUserDefaults(suiteName = APP_GROUP)
        ?: NSUserDefaults.standardUserDefaults,
) : SettingsStore {
    override suspend fun get(): FukahaSettings {
        migrateCobaltPublicDefaultIfNeeded()
        val action = defaults.stringForKey(KEY_DEFAULT_ACTION)?.let {
            runCatching { ShareAction.valueOf(it) }.getOrNull()
        } ?: ShareAction.Ask
        return FukahaSettings(
            defaultAction = action,
            preferredFixers = parseFixers(defaults.stringForKey(KEY_PREFERRED_FIXERS)),
            cobaltBaseUrl = defaults.stringForKey(KEY_COBALT)
                ?: FukahaSettings.DEFAULT_COBALT_BASE_URL,
            cobaltApiKey = defaults.stringForKey(KEY_COBALT_API_KEY).orEmpty(),
            resolveShortLinks = if (defaults.objectForKey(KEY_RESOLVE_SHORT) == null) {
                true
            } else {
                defaults.boolForKey(KEY_RESOLVE_SHORT)
            },
            language = defaults.stringForKey(KEY_LANGUAGE)?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.System,
            theme = defaults.stringForKey(KEY_THEME)?.let {
                runCatching { AppTheme.valueOf(it) }.getOrNull()
            } ?: AppTheme.System,
            deleteCacheAfterShare = if (defaults.objectForKey(KEY_DELETE_CACHE) == null) {
                true
            } else {
                defaults.boolForKey(KEY_DELETE_CACHE)
            },
            onboardingCompleted = defaults.boolForKey(KEY_ONBOARDING_COMPLETED),
            checkUpdatesOnLaunch = if (defaults.objectForKey(KEY_CHECK_UPDATES) == null) {
                true
            } else {
                defaults.boolForKey(KEY_CHECK_UPDATES)
            },
            skippedUpdateVersion = defaults.stringForKey(KEY_SKIPPED_UPDATE).orEmpty(),
            lastUpdateCheckEpochMs = defaults.objectForKey(KEY_LAST_UPDATE_CHECK)?.let {
                defaults.integerForKey(KEY_LAST_UPDATE_CHECK)
            } ?: 0L,
        ).withDownloadClamped()
    }

    override suspend fun update(transform: (FukahaSettings) -> FukahaSettings) {
        val next = transform(get()).withDownloadClamped()
        defaults.setObject(next.defaultAction.name, KEY_DEFAULT_ACTION)
        defaults.setObject(serializeFixers(next.preferredFixers), KEY_PREFERRED_FIXERS)
        defaults.setObject(next.cobaltBaseUrl, KEY_COBALT)
        defaults.setObject(next.cobaltApiKey, KEY_COBALT_API_KEY)
        defaults.setBool(next.resolveShortLinks, KEY_RESOLVE_SHORT)
        defaults.setObject(next.language.name, KEY_LANGUAGE)
        defaults.setObject(next.theme.name, KEY_THEME)
        defaults.setBool(next.deleteCacheAfterShare, KEY_DELETE_CACHE)
        defaults.setBool(next.onboardingCompleted, KEY_ONBOARDING_COMPLETED)
        defaults.setBool(next.checkUpdatesOnLaunch, KEY_CHECK_UPDATES)
        defaults.setObject(next.skippedUpdateVersion, KEY_SKIPPED_UPDATE)
        defaults.setInteger(next.lastUpdateCheckEpochMs, KEY_LAST_UPDATE_CHECK)
        defaults.synchronize()
    }

    private fun migrateCobaltPublicDefaultIfNeeded() {
        if (defaults.boolForKey(KEY_COBALT_PUBLIC_CLEARED)) return
        val stored = defaults.stringForKey(KEY_COBALT)
        if (stored == null || FukahaSettings.isLegacyPublicCobaltBaseUrl(stored)) {
            defaults.setObject(FukahaSettings.DEFAULT_COBALT_BASE_URL, KEY_COBALT)
        }
        if (defaults.stringForKey(KEY_DEFAULT_ACTION) == ShareAction.Download.name &&
            !FukahaSettings.isValidCobaltBaseUrl(defaults.stringForKey(KEY_COBALT).orEmpty())
        ) {
            defaults.setObject(ShareAction.Ask.name, KEY_DEFAULT_ACTION)
        }
        defaults.setBool(true, KEY_COBALT_PUBLIC_CLEARED)
        defaults.synchronize()
    }

    private fun serializeFixers(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    private fun parseFixers(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

    companion object {
        const val APP_GROUP = "group.com.shenepoy.fukaha"
        private const val KEY_DEFAULT_ACTION = "default_action"
        private const val KEY_PREFERRED_FIXERS = "preferred_fixers"
        private const val KEY_COBALT = "cobalt_base_url"
        private const val KEY_COBALT_API_KEY = "cobalt_api_key"
        private const val KEY_RESOLVE_SHORT = "resolve_short_links"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"
        private const val KEY_DELETE_CACHE = "delete_cache_after_share"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_CHECK_UPDATES = "check_updates_on_launch"
        private const val KEY_SKIPPED_UPDATE = "skipped_update_version"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check_epoch_ms"
        private const val KEY_COBALT_PUBLIC_CLEARED = "cobalt_public_default_cleared"
    }
}
