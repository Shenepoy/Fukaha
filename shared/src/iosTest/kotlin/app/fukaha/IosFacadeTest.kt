package app.fukaha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosFacadeTest {
    @Test fun everyCatalogSourceReachesTheIosCallback() = runTest {
        withContext(Dispatchers.Default) {
            val facade = FukahaIosFacade()
            try {
                val catalog = EmbedCatalog.bundled()
                for (platform in catalog.platformKeys()) {
                    val original = "${catalog.platform(platform)!!.href}/catalog-validation/123"
                    for (service in catalog.activeServices(platform)) {
                        val host = service.normalizedHost()
                        assertEquals(host, facade.selectedFixer(platform, host))
                        val result = CompletableDeferred<String?>()
                        facade.prepareWithPreferences(original, "", false, mapOf(platform to host), emptyList(), false) { _, embed, _, error ->
                            if (error != null) result.completeExceptionally(IllegalStateException(error))
                            else result.complete(embed)
                        }
                        assertEquals("$host/catalog-validation/123", withTimeout(5_000) { result.await() })
                    }
                }
            } finally { facade.close() }
        }
    }

    @Test fun pickerAndPreparedLinkResolveTheSameSavedSource() = runTest {
        withContext(Dispatchers.Default) {
            val facade = FukahaIosFacade()
            try {
                assertEquals("https://fixupx.com", facade.selectedFixer("x", "fixupx.com"))
                assertEquals("https://fixupx.com", facade.selectedFixer("x", "FxEmbed"))
                assertEquals("https://fixupx.com", facade.selectedFixer("x", "https://fxtwitter.com"))
                val result = CompletableDeferred<String?>()
                facade.prepare("https://x.com/example/status/123", "", false,
                    mapOf("x" to "fixupx.com", "youtube" to "koutube.com")) { _, embed, _, error ->
                    if (error != null) result.completeExceptionally(IllegalStateException(error))
                    else result.complete(embed)
                }
                assertEquals("https://fixupx.com/example/status/123", withTimeout(5_000) { result.await() })
            } finally { facade.close() }
        }
    }

    @Test fun preferencesReachNativePreparation() = runTest {
        withContext(Dispatchers.Default) {
            val facade = FukahaIosFacade()
            try {
                val result = CompletableDeferred<Pair<String?, String?>>()
                facade.prepareWithPreferences("https://youtube.com/watch?v=dQw4w9WgXcQ&t=42", "", false,
                    emptyMap(), listOf("youtube"), true) { clean, embed, _, error ->
                    if (error != null) result.completeExceptionally(IllegalStateException(error))
                    else result.complete(clean to embed)
                }
                assertEquals("https://youtu.be/dQw4w9WgXcQ?t=42" to null, withTimeout(5_000) { result.await() })
            } finally { facade.close() }
        }
    }

    @Test fun closedFacadeDoesNotStartWorkOrDeliverCallbacks() = runTest {
        withContext(Dispatchers.Default) {
            val facade = FukahaIosFacade()
            val result = CompletableDeferred<Unit>()
            facade.close()
            facade.prepare("https://x.com/example/status/123", "", false, emptyMap()) { _, _, _, _ ->
                result.complete(Unit)
            }
            delay(100)
            assertFalse(result.isCompleted)
        }
    }
}
