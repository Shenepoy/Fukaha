package app.fukaha.web

import app.fukaha.EmbedHealthStatus
import app.fukaha.EmbedService
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import kotlin.js.Promise
import kotlin.test.*

class WebSourcesTest {
    @AfterTest
    fun cleanup() {
        localStorage.removeItem("fukaha_source_preferences")
        localStorage.removeItem("fukaha_embed_health")
    }

    @Test
    fun favoritesAndOrderSurviveNewStoreAndCatalogChanges() {
        val a = EmbedService(name = "A", host = "https://a.example")
        val b = EmbedService(name = "B", host = "https://b.example")
        val c = EmbedService(name = "C", host = "https://c.example")
        val services = listOf(a, b, c)
        val preferences = WebSourcePreferences()
            .toggleFavorite("x", b.normalizedHost())
            .toggleFavorite("x", c.normalizedHost())
            .move("x", c.normalizedHost(), -1, services)
        assertTrue(WebSourcePreferencesStore().save(preferences))
        val restored = WebSourcePreferencesStore().get()
        assertEquals(listOf(c, b, a), restored.sorted("x", services))
        assertEquals(listOf(a, b, c), restored.sorted("youtube", services))
        val new = EmbedService(name = "D", host = "https://d.example")
        assertEquals(listOf(c, a, new), restored.sorted("x", listOf(a, c, new)))
        // Removing a star does not discard the chosen display order.
        assertEquals(listOf(b, c, a), restored.toggleFavorite("x", c.normalizedHost()).sorted("x", services))
        assertEquals(restored, restored.move("x", b.normalizedHost(), 1, services))
    }

    @Test
    fun unsuccessfulAttemptSurvivesReloadWithoutReplacingGoodResults() {
        val good = mapOf("fixupx.com" to EmbedHealthStatus.Alive)
        assertTrue(WebHealthStore().save(good, 1000))
        assertTrue(WebHealthStore().save(mapOf("fixupx.com" to EmbedHealthStatus.Unknown), 2000))
        val restored = WebHealthStore()
        assertEquals(2000L, restored.lastAttemptAt())
        assertTrue(restored.lastAttemptFailed())
        assertEquals(1000L, restored.get().checkedAtEpochMs)
        assertEquals(EmbedHealthStatus.Alive, restored.get().statusOf("https://fixupx.com"))
        assertTrue(restored.save(good, 3000))
        assertFalse(WebHealthStore().lastAttemptFailed())
        assertEquals(3000L, WebHealthStore().lastAttemptAt())
    }

    @Test
    fun firstUnsuccessfulCheckIsRememberedAndAllowsRetry() {
        assertTrue(WebHealthStore().save(emptyMap(), 1000))
        assertEquals(1000L, WebHealthStore().lastAttemptAt())
        assertNull(WebHealthStore().get().checkedAtEpochMs)
        assertTrue(WebHealthStore().lastAttemptFailed())
    }

    @Test
    fun olderSnapshotsAndCorruptStorageAreHandled() {
        localStorage.setItem("fukaha_embed_health", """{"statuses":{"fixupx.com":"Alive"},"checkedAt":1234}""")
        assertEquals(1234L, WebHealthStore().lastAttemptAt())
        assertEquals(EmbedHealthStatus.Alive, WebHealthStore().get().statusOf("fixupx.com"))
        localStorage.setItem("fukaha_source_preferences", "broken")
        assertEquals(WebSourcePreferences(), WebSourcePreferencesStore().get())
        localStorage.setItem("fukaha_embed_health", "broken")
        assertNull(WebHealthStore().lastAttemptAt())
    }

    @Test
    fun browserProbesRetryHeadAndDoNotRequireAbortSignalTimeout(): Promise<Unit> = MainScope().promise {
        val originalFetch = window.asDynamic().fetch
        val signal: dynamic = js("AbortSignal")
        val originalTimeout = signal.timeout
        val methods = mutableListOf<String>()
        try {
            signal.timeout = js("undefined")
            window.asDynamic().fetch = { _: dynamic, init: dynamic ->
                methods.add(init.method as String)
                if (init.method == "HEAD") Promise.reject(Throwable("blocked HEAD"))
                else Promise.resolve(Unit)
            }
            assertEquals(EmbedHealthStatus.Alive, WebEmbedHealth.probe("example.com"))
            assertEquals(listOf("HEAD", "GET"), methods)
        } finally {
            window.asDynamic().fetch = originalFetch
            signal.timeout = originalTimeout
        }
    }

    @Test
    fun blockedBrowserRequestsStayUnverifiedAndProgressCompletes(): Promise<Unit> = MainScope().promise {
        val originalFetch = window.asDynamic().fetch
        try {
            window.asDynamic().fetch = { _: dynamic, _: dynamic -> Promise.reject(Throwable("blocked")) }
            var completed = 0
            val results = WebEmbedHealth.refresh(listOf("https://example.com", "https://other.example")) { progress, _ ->
                completed = progress.completedCount
                assertEquals(0, progress.deadCount)
            }
            assertEquals(2, completed)
            assertTrue(results.values.all { it == EmbedHealthStatus.Unknown })
        } finally {
            window.asDynamic().fetch = originalFetch
        }
    }
}
