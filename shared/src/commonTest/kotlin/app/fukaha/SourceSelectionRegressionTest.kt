package app.fukaha

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SourceSelectionRegressionTest {
    @Test
    fun explicitSourceSurvivesFailedCheck() {
        val catalog = EmbedCatalog.bundled()
        val health = mapOf("https://fixvx.com" to EmbedHealthStatus.Dead, "https://fixupx.com" to EmbedHealthStatus.Alive)
        assertEquals("https://fixvx.com/a/status/1", catalog.rewriteToEmbed("https://x.com/a/status/1", "https://fixvx.com", health))
    }

    @Test
    fun persistedBareHostStillSelectsTheSource() {
        assertEquals("https://fixvx.com", EmbedCatalog.bundled().serviceForHost("x", "FIXVX.COM/")?.normalizedHost())
    }

    @Test
    fun rejectedHeadRetriesGet() = runTest {
        val methods = mutableListOf<EmbedHealthRequestMethod>()
        val checker = EmbedHealthChecker.createForTests(EmbedCatalog.bundled()) { _, method ->
            methods += method
            if (method == EmbedHealthRequestMethod.Head) 403 else 200
        }
        assertEquals(EmbedHealthStatus.Alive, checker.probe("https://example.com"))
        assertEquals(listOf(EmbedHealthRequestMethod.Head, EmbedHealthRequestMethod.Get), methods)
    }

    @Test
    fun missingHomepageDoesNotProveEmbedFailure() = runTest {
        val checker = EmbedHealthChecker.createForTests(EmbedCatalog.bundled()) { _, _ -> 404 }
        assertEquals(EmbedHealthStatus.Unknown, checker.probe("https://example.com"))
    }
}
