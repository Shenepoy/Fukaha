package app.fukaha.android

import app.fukaha.EmbedHealthChecker
import app.fukaha.EmbedHealthPolicy
import app.fukaha.EmbedHealthProgress
import app.fukaha.EmbedHealthSnapshot
import app.fukaha.EmbedHealthStore
import app.fukaha.PlatformClock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs gentle embedder probes on demand and when due (every 6h while foregrounded).
 */
class EmbedHealthController(
    private val store: EmbedHealthStore,
    private val scope: CoroutineScope,
    private val checker: EmbedHealthChecker = EmbedHealthChecker.create(),
) {
    private var job: Job? = null
    private var backgroundCancelJob: Job? = null

    private val _inProgress = MutableStateFlow(false)
    val inProgress: StateFlow<Boolean> = _inProgress.asStateFlow()

    private val _progress = MutableStateFlow<EmbedHealthProgress?>(null)
    val progress: StateFlow<EmbedHealthProgress?> = _progress.asStateFlow()

    /** True when the last run reached nothing, so the user can retry right away. */
    private val _lastRunUnreachable = MutableStateFlow(false)
    val lastRunUnreachable: StateFlow<Boolean> = _lastRunUnreachable.asStateFlow()

    /** Stable instance so Compose collection is not restarted on recomposition. */
    private val snapshots: Flow<EmbedHealthSnapshot> = store.observe()

    fun observeSnapshot(): Flow<EmbedHealthSnapshot> = snapshots

    fun startAutoIfDue() {
        scope.launch {
            val snap = store.get()
            if (EmbedHealthPolicy.isDue(snap.checkedAtEpochMs)) {
                refresh()
            }
        }
    }

    fun refresh() {
        if (job?.isActive == true) return
        job = scope.launch {
            val previousUnreachable = _lastRunUnreachable.value
            _inProgress.value = true
            _lastRunUnreachable.value = false
            _progress.value = EmbedHealthProgress(
                currentHost = "",
                currentIndex = 0,
                total = checker.uniqueHosts().size,
            )
            try {
                val results = checker.refresh { step ->
                    _progress.value = step
                }
                val usable = EmbedHealthPolicy.isUsableResult(results)
                _lastRunUnreachable.value = !usable
                if (usable) {
                    store.save(results, PlatformClock.epochMillis(), checker.details())
                }
            } catch (cancelled: CancellationException) {
                // In-app task switches (the Settings test link) must not look like
                // every embedder died, and must not write a failed snapshot.
                _lastRunUnreachable.value = previousUnreachable
                throw cancelled
            } finally {
                _inProgress.value = false
                _progress.value = null
            }
        }
    }

    /**
     * ShareActivity is `singleInstance`, so opening the test link stops the
     * Settings task before the overlay starts. Wait out that hand-off — and
     * ProcessLifecycleOwner's 700ms stop delay — before aborting probes.
     */
    fun stayForegrounded() {
        backgroundCancelJob?.cancel()
        backgroundCancelJob = null
    }

    fun scheduleCancelIfBackgrounded() {
        if (backgroundCancelJob?.isActive == true) return
        backgroundCancelJob = scope.launch {
            delay(BACKGROUND_CANCEL_DELAY_MS)
            cancel()
        }
    }

    fun cancel() {
        backgroundCancelJob?.cancel()
        backgroundCancelJob = null
        job?.cancel()
        job = null
        _inProgress.value = false
        _progress.value = null
    }

    companion object {
        const val BACKGROUND_CANCEL_DELAY_MS = 5_000L
    }
}
