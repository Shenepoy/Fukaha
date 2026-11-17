package app.fukaha

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.fukahaHealthDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fukaha_embed_health",
)

class AndroidEmbedHealthStore(private val context: Context) : EmbedHealthStore {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val details = stringPreferencesKey("embed_health_details")
        val statuses = stringPreferencesKey("embed_health_statuses")
        val checkedAt = longPreferencesKey("embed_health_checked_at")
    }

    override fun observe(): Flow<EmbedHealthSnapshot> =
        context.fukahaHealthDataStore.data.map { prefs -> prefs.toSnapshot() }

    override suspend fun get(): EmbedHealthSnapshot =
        context.fukahaHealthDataStore.data.map { it.toSnapshot() }.first()

    override suspend fun save(
        statuses: Map<String, EmbedHealthStatus>,
        checkedAtEpochMs: Long,
        details: Map<String, SourceCheckDetail>,
    ) {
        val normalized = statuses.mapKeys { EmbedHealthKeys.normalize(it.key) }
        context.fukahaHealthDataStore.edit { prefs ->
            prefs[Keys.statuses] = json.encodeToString(
                normalized.mapValues { it.value.name },
            )
            prefs[Keys.checkedAt] = checkedAtEpochMs
            prefs[Keys.details] = json.encodeToString(details)
        }
    }

    private fun Preferences.toSnapshot(): EmbedHealthSnapshot {
        val statuses = this[Keys.statuses]?.let { raw ->
            runCatching {
                json.decodeFromString<Map<String, String>>(raw).mapNotNull { (host, name) ->
                    val status = runCatching { EmbedHealthStatus.valueOf(name) }.getOrNull()
                        ?: return@mapNotNull null
                    EmbedHealthKeys.normalize(host) to status
                }.toMap()
            }.getOrDefault(emptyMap())
        } ?: emptyMap()
        return EmbedHealthSnapshot(
            statuses = statuses,
            checkedAtEpochMs = this[Keys.checkedAt],
            details = this[Keys.details]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, SourceCheckDetail>>(raw) }.getOrDefault(emptyMap())
            }.orEmpty(),
        )
    }
}
