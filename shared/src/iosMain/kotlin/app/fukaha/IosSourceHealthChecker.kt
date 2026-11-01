package app.fukaha

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Swift-facing, cancellable source checks. Never reads or changes preferred sources. */
class IosSourceHealthChecker internal constructor(
    private val scope: CoroutineScope,
    private val createChecker: () -> EmbedHealthChecker,
) {
    constructor() : this(
        CoroutineScope(SupervisorJob() + Dispatchers.Main),
        { EmbedHealthChecker.create() },
    )

    private var job: Job? = null

    fun refresh(
        onProgress: (EmbedHealthProgress) -> Unit,
        onResult: (snapshotJson: String?, error: String?) -> Unit,
    ) {
        if (job?.isActive == true) return
        job = scope.launch {
            var checker: EmbedHealthChecker? = null
            try {
                // Each run owns its HTTP client and details, including after cancellation.
                checker = createChecker()
                onProgress(EmbedHealthProgress("", 0, checker.uniqueHosts().size))
                val statuses = checker.refresh { step ->
                    ensureActive()
                    onProgress(step)
                }
                ensureActive()
                val snapshot = EmbedHealthSnapshot(statuses, PlatformClock.epochMillis(), checker.details())
                onResult(Json.encodeToString(snapshot), null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ensureActive()
                onResult(null, error.message ?: "Source check failed")
            } finally {
                checker?.close()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun close() { scope.cancel() }
}
