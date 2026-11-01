package app.fukaha

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IosSourceHealthCheckerTest {
    private val catalog = EmbedCatalog(mapOf("x" to EmbedPlatform(
        name = "X",
        services = listOf("ok.example", "blocked.example", "failed.example")
            .map { EmbedService(name = it, host = "https://$it") },
    )))

    @Test fun reportsProgressAndSerializesTheActualProbeOutcomes() = runTest {
        val checker = IosSourceHealthChecker(CoroutineScope(coroutineContext + SupervisorJob())) {
            EmbedHealthChecker.createForTests(catalog) { url, _ ->
                when {
                    "ok.example" in url -> 200
                    "blocked.example" in url -> 403
                    else -> 503
                }
            }
        }
        val progress = mutableListOf<Int>()
        var json: String? = null
        try {
            checker.refresh({ progress += it.completedCount }) { value, error ->
                assertNull(error)
                json = value
            }
            advanceUntilIdle()
            assertEquals(listOf(0, 1, 2, 3), progress)
            val result = Json.decodeFromString<EmbedHealthSnapshot>(assertNotNull(json))
            assertEquals(EmbedHealthStatus.Alive, result.statusOf("ok.example"))
            assertEquals(EmbedHealthStatus.Unknown, result.statusOf("blocked.example"))
            assertEquals(EmbedHealthStatus.Dead, result.statusOf("failed.example"))
            assertEquals(SourceCheckReason.Blocked, result.details["https://blocked.example"]?.reason)
            assertNotNull(result.checkedAtEpochMs)
        } finally { checker.close() }
    }

    @Test fun duplicateStartIsIgnoredAndCancellationCannotDeliverStaleResults() = runTest {
        var starts = 0
        var callbacks = 0
        val checker = IosSourceHealthChecker(CoroutineScope(coroutineContext + SupervisorJob())) {
            starts++
            EmbedHealthChecker.createForTests(catalog) { _, _ -> delay(100); 200 }
        }
        try {
            checker.refresh({}) { _, _ -> callbacks++ }
            checker.refresh({}) { _, _ -> callbacks++ }
            runCurrent()
            assertEquals(1, starts)
            checker.cancel()
            advanceUntilIdle()
            assertEquals(0, callbacks)
            checker.refresh({}) { _, _ -> callbacks++ }
            advanceUntilIdle()
            assertEquals(2, starts)
            assertEquals(1, callbacks)
            checker.close()
            checker.refresh({}) { _, _ -> callbacks++ }
            advanceUntilIdle()
            assertEquals(2, starts)
            assertEquals(1, callbacks)
        } finally { checker.close() }
    }

    @Test fun failureIsReturnedSoTheAppCanRetry() = runTest {
        val checker = IosSourceHealthChecker(CoroutineScope(coroutineContext + SupervisorJob())) {
            error("Cannot start checker")
        }
        var failure: String? = null
        try {
            checker.refresh({}) { value, error -> assertNull(value); failure = error }
            advanceUntilIdle()
            assertTrue(assertNotNull(failure).contains("Cannot start checker"))
        } finally { checker.close() }
    }
}
