package app.fukaha

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSettingsStoreConcurrencyTest {
    @Test
    fun concurrentTransformsReadTheLatestPersistedSnapshot() = runTest {
        val directory = Files.createTempDirectory("fukaha-settings-test").toFile()
        val job = SupervisorJob()
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job + Dispatchers.IO),
                produceFile = { directory.resolve("settings.preferences_pb") },
            )
            val store = AndroidSettingsStore(dataStore)
            store.get()
            (1..50).map {
                async {
                    store.update { current -> current.copy(lastUpdateCheckEpochMs = current.lastUpdateCheckEpochMs + 1) }
                }
            }.awaitAll()
            assertEquals(50L, store.get().lastUpdateCheckEpochMs)

            store.update { it.copy(disabledEmbedPlatforms = setOf("x"), useOfficialShortLinks = true) }
            val reopened = AndroidSettingsStore(dataStore)
            assertEquals(setOf("x"), reopened.get().disabledEmbedPlatforms)
            assertEquals(true, reopened.get().useOfficialShortLinks)
            val before = store.get()
            store.update { it.copy(lastUpdateCheckEpochMs = 100, theme = AppTheme.Dark) }
            store.update { it.applyChangesFrom(before, before.copy(language = AppLanguage.Arabic)) }
            assertEquals(100L, store.get().lastUpdateCheckEpochMs)
            assertEquals(AppTheme.Dark, store.get().theme)
            assertEquals(AppLanguage.Arabic, store.get().language)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
