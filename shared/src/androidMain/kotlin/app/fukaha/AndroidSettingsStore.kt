package app.fukaha

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.fukahaDataStore: DataStore<Preferences> by preferencesDataStore(name = "fukaha_settings")

class AndroidSettingsStore internal constructor(private val dataStore: DataStore<Preferences>) : SettingsStore {
    constructor(context: Context) : this(context.fukahaDataStore)
    private val json = Json { ignoreUnknownKeys = true }
    private val migrationLock = Mutex()
    private var migrated = false

    private object Keys {
        val defaultAction = stringPreferencesKey("default_action")
        val preferredFixers = stringPreferencesKey("preferred_fixers")
        val cobaltBaseUrl = stringPreferencesKey("cobalt_base_url")
        val cobaltApiKey = stringPreferencesKey("cobalt_api_key")
        val resolveShortLinks = booleanPreferencesKey("resolve_short_links")
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val deleteCacheAfterShare = booleanPreferencesKey("delete_cache_after_share")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val checkUpdatesOnLaunch = booleanPreferencesKey("check_updates_on_launch")
        val skippedUpdateVersion = stringPreferencesKey("skipped_update_version")
        val lastUpdateCheckEpochMs = longPreferencesKey("last_update_check_epoch_ms")
        /** One-time: old builds defaulted language writes to English; prefer System now. */
        val languageFollowsSystemMigrated = booleanPreferencesKey("language_follows_system_migrated")
        /** One-time: clear former public api.cobalt.tools default. */
        val cobaltPublicDefaultCleared = booleanPreferencesKey("cobalt_public_default_cleared")
    }

    override suspend fun get(): FukahaSettings {
        migrateIfNeeded()
        return read(dataStore.data.first())
    }

    private fun read(prefs: Preferences): FukahaSettings =
        FukahaSettings(
            defaultAction = prefs[Keys.defaultAction]?.let { runCatching { ShareAction.valueOf(it) }.getOrNull() }
                ?: ShareAction.Ask,
            preferredFixers = prefs[Keys.preferredFixers]?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap())
            } ?: emptyMap(),
            cobaltBaseUrl = prefs[Keys.cobaltBaseUrl] ?: FukahaSettings.DEFAULT_COBALT_BASE_URL,
            cobaltApiKey = prefs[Keys.cobaltApiKey].orEmpty(),
            resolveShortLinks = prefs[Keys.resolveShortLinks] ?: true,
            language = AppLanguage.fromPersistedValue(prefs[Keys.language]),
            theme = prefs[Keys.theme]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                ?: AppTheme.System,
            deleteCacheAfterShare = prefs[Keys.deleteCacheAfterShare] ?: true,
            onboardingCompleted = prefs[Keys.onboardingCompleted] ?: false,
            checkUpdatesOnLaunch = prefs[Keys.checkUpdatesOnLaunch] ?: true,
            skippedUpdateVersion = prefs[Keys.skippedUpdateVersion].orEmpty(),
            lastUpdateCheckEpochMs = prefs[Keys.lastUpdateCheckEpochMs] ?: 0L,
        ).withDownloadClamped()

    /**
     * Migrations are persisted-flag guarded, so once this process has run them the
     * result cannot change again; skipping repeat runs keeps [get] to a single read.
     */
    private suspend fun migrateIfNeeded() {
        if (migrated) return
        migrationLock.withLock {
            if (migrated) return
            dataStore.edit { prefs ->
                migrateLanguageDefault(prefs)
                migrateCobaltPublicDefault(prefs)
            }
            migrated = true
        }
    }

    private fun migrateLanguageDefault(prefs: MutablePreferences) {
        if (prefs[Keys.languageFollowsSystemMigrated] == true) return
        val stored = prefs[Keys.language]
        // Pre-System builds always persisted English as the default on any save.
        prefs[Keys.language] = migrateLegacyLanguageValue(stored)
        prefs[Keys.languageFollowsSystemMigrated] = true
    }

    private fun migrateCobaltPublicDefault(prefs: MutablePreferences) {
        if (prefs[Keys.cobaltPublicDefaultCleared] == true) return
        val stored = prefs[Keys.cobaltBaseUrl]
        if (stored == null || FukahaSettings.isLegacyPublicCobaltBaseUrl(stored)) {
            prefs[Keys.cobaltBaseUrl] = FukahaSettings.DEFAULT_COBALT_BASE_URL
        }
        if (prefs[Keys.defaultAction] == ShareAction.Download.name &&
            !FukahaSettings.isValidCobaltBaseUrl(prefs[Keys.cobaltBaseUrl].orEmpty())
        ) {
            prefs[Keys.defaultAction] = ShareAction.Ask.name
        }
        prefs[Keys.cobaltPublicDefaultCleared] = true
    }

    override suspend fun update(transform: (FukahaSettings) -> FukahaSettings) {
        migrateIfNeeded()
        dataStore.edit { prefs ->
            val next = transform(read(prefs)).withDownloadClamped()
            prefs[Keys.defaultAction] = next.defaultAction.name
            prefs[Keys.preferredFixers] = json.encodeToString(next.preferredFixers)
            prefs[Keys.cobaltBaseUrl] = next.cobaltBaseUrl
            prefs[Keys.cobaltApiKey] = next.cobaltApiKey
            prefs[Keys.resolveShortLinks] = next.resolveShortLinks
            prefs[Keys.language] = next.language.name
            prefs[Keys.theme] = next.theme.name
            prefs[Keys.deleteCacheAfterShare] = next.deleteCacheAfterShare
            prefs[Keys.onboardingCompleted] = next.onboardingCompleted
            prefs[Keys.checkUpdatesOnLaunch] = next.checkUpdatesOnLaunch
            prefs[Keys.skippedUpdateVersion] = next.skippedUpdateVersion
            prefs[Keys.lastUpdateCheckEpochMs] = next.lastUpdateCheckEpochMs
        }
    }
}
