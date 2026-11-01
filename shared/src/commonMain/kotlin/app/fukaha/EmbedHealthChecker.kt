package app.fukaha

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.request.prepareHead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

internal enum class EmbedHealthRequestMethod {
    Head,
    Get,
}

internal data class SourceResponse(val status: Int, val finalHost: String?)
internal data class SourceProbe(val status: EmbedHealthStatus, val detail: SourceCheckDetail)

class EmbedHealthChecker private constructor(
    private val catalog: EmbedCatalog,
    private val requestStatus: suspend (String, EmbedHealthRequestMethod) -> SourceResponse,
    private val closeRequest: () -> Unit,
) {
    /** The catalog never changes at runtime, so the probe list is built once. */
    private val hosts: List<String> by lazy {
        catalog.platformKeys()
            .flatMap { catalog.activeServices(it) }
            .map { EmbedHealthKeys.normalize(it.normalizedHost()) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    fun uniqueHosts(): List<String> = hosts

    private val runDetails = mutableMapOf<String, SourceCheckDetail>()
    fun details(): Map<String, SourceCheckDetail> = runDetails.toMap()

    /**
     * Probes unique fixer hosts with a small bounded worker pool.
     * [onProgress] is invoked as each result is recorded.
     * Throws [CancellationException] if cancelled mid-run (partial results not saved by caller).
     */
    suspend fun refresh(
        onProgress: ((EmbedHealthProgress) -> Unit)? = null,
    ): Map<String, EmbedHealthStatus> = coroutineScope {
        val hosts = uniqueHosts()
        if (hosts.isEmpty()) return@coroutineScope emptyMap()

        runDetails.clear()
        val results = mutableMapOf<String, EmbedHealthStatus>()
        val cursorMutex = Mutex()
        val resultsMutex = Mutex()
        var nextIndex = 0
        var alive = 0
        var dead = 0

        suspend fun takeNextHost(): String? = cursorMutex.withLock {
            if (nextIndex >= hosts.size) {
                null
            } else {
                hosts[nextIndex++]
            }
        }

        List(minOf(EmbedHealthPolicy.PROBE_CONCURRENCY, hosts.size)) {
            async {
                while (true) {
                    coroutineContext.ensureActive()
                    val host = takeNextHost() ?: break
                    val probe = probeWithDetail(host)
                    val status = probe.status
                    resultsMutex.withLock {
                        results[host] = status
                        runDetails[host] = probe.detail
                        if (status == EmbedHealthStatus.Alive) alive++
                        if (status == EmbedHealthStatus.Dead) dead++
                        onProgress?.invoke(
                            EmbedHealthProgress(
                                currentHost = host,
                                currentIndex = results.size,
                                total = hosts.size,
                                aliveCount = alive,
                                deadCount = dead,
                            ),
                        )
                    }
                }
            }
        }.awaitAll()

        hosts.associateWith { results.getValue(it) }
    }

    suspend fun probe(host: String): EmbedHealthStatus = probeWithDetail(host).status

    internal suspend fun probeWithDetail(host: String): SourceProbe {
        val url = EmbedHealthKeys.probeUrl(host)
        var failure = SourceCheckReason.NetworkError
        suspend fun request(method: EmbedHealthRequestMethod): SourceResponse? = try {
            requestStatus(url, method)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            failure = if (error::class.simpleName.orEmpty().contains("Timeout")) SourceCheckReason.TimedOut
                else SourceCheckReason.NetworkError
            null
        }
        val head = request(EmbedHealthRequestMethod.Head)
        val response = if (head?.status in 200..299) head else request(EmbedHealthRequestMethod.Get) ?: head
        if (response == null) return SourceProbe(EmbedHealthStatus.Dead, SourceCheckDetail(failure))
        val redirected = response.finalHost != null &&
            response.finalHost.removePrefix("www.") != UrlCleaner.hostOf(url)?.removePrefix("www.")
        val reason = when {
            redirected -> SourceCheckReason.Redirected
            response.status == 401 || response.status == 403 || response.status == 429 -> SourceCheckReason.Blocked
            response.status in 200..299 -> SourceCheckReason.Responded
            else -> SourceCheckReason.HttpError
        }
        return SourceProbe(
            if (redirected) EmbedHealthStatus.Unknown else classifyHttpStatus(response.status),
            SourceCheckDetail(reason, response.status, response.finalHost),
        )
    }

    fun close() {
        closeRequest()
    }

    companion object {
        fun create(catalog: EmbedCatalog = EmbedCatalog.bundled()): EmbedHealthChecker =
            probeHttpClient().let { client ->
                EmbedHealthChecker(
                    catalog = catalog,
                    requestStatus = { url, method ->
                        when (method) {
                            EmbedHealthRequestMethod.Head -> client.prepareHead(url).execute { SourceResponse(it.status.value, it.call.request.url.host) }
                            EmbedHealthRequestMethod.Get -> client.prepareGet(url).execute { SourceResponse(it.status.value, it.call.request.url.host) }
                        }
                    },
                    closeRequest = client::close,
                )
            }

        internal fun createForTests(
            catalog: EmbedCatalog,
            requestStatus: suspend (String, EmbedHealthRequestMethod) -> Int,
        ): EmbedHealthChecker = EmbedHealthChecker(catalog, { url, method -> SourceResponse(requestStatus(url, method), UrlCleaner.hostOf(url)) }, {})

        internal fun createWithResponsesForTests(
            catalog: EmbedCatalog,
            request: suspend (String, EmbedHealthRequestMethod) -> SourceResponse,
        ) = EmbedHealthChecker(catalog, request, {})

        internal fun classifyHttpStatus(statusCode: Int?): EmbedHealthStatus =
            when (statusCode) {
                in 200..299 -> EmbedHealthStatus.Alive
                null, in 500..599 -> EmbedHealthStatus.Dead
                // A challenge, rate limit, or absent homepage says nothing about post embeds.
                else -> EmbedHealthStatus.Unknown
            }

        fun probeHttpClient(): HttpClient = HttpClient {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout) {
                requestTimeoutMillis = EmbedHealthPolicy.PROBE_TIMEOUT_MS
                connectTimeoutMillis = EmbedHealthPolicy.PROBE_TIMEOUT_MS
                socketTimeoutMillis = EmbedHealthPolicy.PROBE_TIMEOUT_MS
            }
        }

        /** Pure helper for tests: HTTP layer succeeded → Alive, else Dead. */
        fun classifyProbeResult(succeeded: Boolean): EmbedHealthStatus =
            if (succeeded) EmbedHealthStatus.Alive else EmbedHealthStatus.Dead
    }
}
