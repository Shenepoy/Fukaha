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
    fun everyCatalogSourceSurvivesAndroidPersistenceAndPreparation() = runTest {
        val directory = Files.createTempDirectory("fukaha-catalog-test").toFile()
        val job = SupervisorJob()
        val bridge = FukahaBridge()
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job + Dispatchers.IO),
                produceFile = { directory.resolve("settings.preferences_pb") },
            )
            val store = AndroidSettingsStore(dataStore)
            val catalog = EmbedCatalog.bundled()
            for (platform in catalog.platformKeys()) {
                val original = "${catalog.platform(platform)!!.href}/catalog-validation/123"
                for (service in catalog.activeServices(platform)) {
                    val host = service.normalizedHost()
                    store.update { it.copy(preferredFixers = it.preferredFixers + (platform to host), resolveShortLinks = false) }
                    val restored = AndroidSettingsStore(dataStore).get()
                    assertEquals(host, restored.preferredFixers[platform])
                    assertEquals("$host/catalog-validation/123", bridge.prepare(original, restored)?.textUrlFor(ShareAction.Embed))
                }
            }
        } finally {
            bridge.close()
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

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
            assertEquals(true, store.get().previewsEnabled)
            (1..50).map {
                async {
                    store.update { current -> current.copy(lastUpdateCheckEpochMs = current.lastUpdateCheckEpochMs + 1) }
                }
            }.awaitAll()
            assertEquals(50L, store.get().lastUpdateCheckEpochMs)

            store.update { it.copy(disabledEmbedPlatforms = setOf("x"), useOfficialShortLinks = true, previewsEnabled = false) }
            val reopened = AndroidSettingsStore(dataStore)
            assertEquals(setOf("x"), reopened.get().disabledEmbedPlatforms)
            assertEquals(true, reopened.get().useOfficialShortLinks)
            assertEquals(false, reopened.get().previewsEnabled)
            val before = store.get()
            store.update { it.copy(lastUpdateCheckEpochMs = 100, theme = AppTheme.Dark) }
            store.update { it.applyChangesFrom(before, before.copy(language = AppLanguage.Arabic)) }
            assertEquals(100L, store.get().lastUpdateCheckEpochMs)
            assertEquals(AppTheme.Dark, store.get().theme)
            assertEquals(AppLanguage.Arabic, store.get().language)
            assertEquals(false, store.get().previewsEnabled)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
