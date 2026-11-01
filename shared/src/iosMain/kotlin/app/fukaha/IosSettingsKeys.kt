package app.fukaha

/**
 * Shared SettingsSnapshot helpers for Swift interop documentation.
 * Swift mirrors keys used by [IosSettingsStore].
 */
object IosSettingsKeys {
    const val APP_GROUP = "group.com.shenepoy.fukaha"
    const val DEFAULT_ACTION = "default_action"
    const val PREFERRED_FIXERS = "preferred_fixers"
    const val COBALT = "cobalt_base_url"
    const val COBALT_API_KEY = "cobalt_api_key"
    const val RESOLVE_SHORT = "resolve_short_links"
    const val LANGUAGE = "language"
    const val THEME = "theme"
    const val DELETE_CACHE = "delete_cache_after_share"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val CHECK_UPDATES = "check_updates_on_launch"
    const val SKIPPED_UPDATE = "skipped_update_version"
    const val LAST_UPDATE_CHECK = "last_update_check_epoch_ms"
}
