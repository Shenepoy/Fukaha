package app.fukaha.web

import app.fukaha.EmbedHealthKeys
import app.fukaha.EmbedHealthPolicy
import app.fukaha.EmbedHealthProgress
import app.fukaha.EmbedHealthSnapshot
import app.fukaha.EmbedHealthStatus
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.js.Promise

/**
 * Reachability probing for embed-fixer hosts, from inside a browser.
 *
 * CORS stops us reading a third-party response, but it does not stop us learning whether one
 * arrived: a `no-cors` fetch resolves with an opaque response for any HTTP status and rejects
 * only on a network-level failure (DNS, refused connection, bad TLS, timeout). Android can also
 * inspect the response status, but the browser cannot do that for a cross-origin opaque response.
 * A same-origin proxy or provider CORS support would be needed to distinguish a browser-visible
 * 404 from a reachable page here.
 *
 * An `<iframe>` cannot do this job: `onload` fires for error pages too, and hosts sending
 * `X-Frame-Options` produce a blocked frame that is indistinguishable from a dead one.
 */
object WebEmbedHealth {
    suspend fun probe(host: String): EmbedHealthStatus {
        val url = EmbedHealthKeys.probeUrl(host)
        // Some hosts reject HEAD outright; fall back to GET when the HEAD request fails.
        val reachable = request(url, "HEAD") || request(url, "GET")
        return if (reachable) EmbedHealthStatus.Alive else EmbedHealthStatus.Unknown
    }

    /**
     * Probes every host once, reporting progress as results land. Order of completion is not the
     * order of [hosts], so progress counts completed probes rather than a position in the list.
     */
    suspend fun refresh(
        hosts: List<String>,
        onProgress: (EmbedHealthProgress, EmbedHealthStatus) -> Unit,
    ): Map<String, EmbedHealthStatus> = coroutineScope {
        val results = LinkedHashMap<String, EmbedHealthStatus>(hosts.size)
        var alive = 0
        var dead = 0
        // Coroutines on Kotlin/JS run on one thread, so a plain cursor needs no synchronisation.
        var next = 0

        List(minOf(EmbedHealthPolicy.PROBE_CONCURRENCY, hosts.size)) {
            async {
                while (true) {
                    val index = next
                    if (index >= hosts.size) break
                    next = index + 1

                    val host = hosts[index]
                    val status = probe(host)
                    results[host] = status
                    if (status == EmbedHealthStatus.Alive) alive++
                    if (status == EmbedHealthStatus.Dead) dead++
                    onProgress(
                        EmbedHealthProgress(
                            currentHost = host,
                            currentIndex = results.size,
                            total = hosts.size,
                            aliveCount = alive,
                            deadCount = dead,
                        ),
                        status,
                    )
                }
            }
        }.awaitAll()

        results
    }

    private suspend fun request(url: String, method: String): Boolean {
        val init: dynamic = js("({})")
        init.method = method
        init.mode = "no-cors"
        init.cache = "no-store"
        init.redirect = "follow"
        init.referrerPolicy = "no-referrer"
        // AbortSignal.timeout is absent in older Safari/WebViews. Always clear our timer,
        // and abort the underlying request when the coroutine is cancelled.
        val controller: dynamic = js("new AbortController()")
        init.signal = controller.signal
        val timer = window.setTimeout({ controller.abort() }, EmbedHealthPolicy.PROBE_TIMEOUT_MS.toInt())
        return try {
            (window.asDynamic().fetch(url, init) as Promise<*>).await()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            false
        } finally {
            window.clearTimeout(timer)
            controller.abort()
        }
    }
}

/** Keep the last attempt separately so a failed run cannot erase a usable snapshot. */
class WebHealthStore {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Stored(
        val statuses: Map<String, String> = emptyMap(),
        val checkedAt: Long? = null,
        val attemptedAt: Long? = null,
        val attemptFailed: Boolean = false,
    )

    private fun read(): Stored = runCatching {
        localStorage.getItem(KEY)?.let { json.decodeFromString<Stored>(it) } ?: Stored()
    }.getOrDefault(Stored())

    fun get(): EmbedHealthSnapshot = read().let { stored ->
        EmbedHealthSnapshot(
            statuses = stored.statuses.mapNotNull { (host, status) ->
                EmbedHealthStatus.entries.firstOrNull { it.name == status }
                    ?.let { EmbedHealthKeys.normalize(host) to it }
            }.toMap(),
            checkedAtEpochMs = stored.checkedAt,
        )
    }

    fun lastAttemptAt(): Long? = read().let { it.attemptedAt ?: it.checkedAt }
    fun lastAttemptFailed(): Boolean = read().attemptFailed

    /** False means storage is unavailable; callers must not claim the result was saved. */
    fun save(statuses: Map<String, EmbedHealthStatus>, checkedAtEpochMs: Long): Boolean {
        val usable = EmbedHealthPolicy.isUsableResult(statuses)
        val previous = read()
        val stored = if (usable) Stored(
            statuses = statuses.mapKeys { EmbedHealthKeys.normalize(it.key) }.mapValues { it.value.name },
            checkedAt = checkedAtEpochMs,
            attemptedAt = checkedAtEpochMs,
        ) else previous.copy(attemptedAt = checkedAtEpochMs, attemptFailed = true)
        return runCatching { localStorage.setItem(KEY, json.encodeToString(stored)) }.isSuccess
    }

    private companion object {
        const val KEY = "fukaha_embed_health"
    }
}
