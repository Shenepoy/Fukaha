package app.fukaha

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LinkProcessor(
    private val catalog: EmbedCatalog = EmbedCatalog.bundled(),
    private val httpClient: HttpClient = defaultHttpClient(),
) {
    fun catalog(): EmbedCatalog = catalog

    suspend fun prepare(
        sharedText: String,
        settings: FukahaSettings,
        health: Map<String, EmbedHealthStatus> = emptyMap(),
    ): PreparedLink? {
        val extracted = UrlCleaner.extractFirstUrl(sharedText) ?: return null
        val resolved = if (settings.resolveShortLinks && UrlCleaner.isShortLink(extracted)) {
            resolveRedirect(extracted) ?: extracted
        } else {
            extracted
        }
        val detected = catalog.detect(resolved)
        val preferred = detected.platformKey?.let { settings.preferredFixers[it] }
        val embedUrl = if (detected.platformKey in settings.disabledEmbedPlatforms) null
            else catalog.rewriteToEmbed(detected.cleanedUrl, preferred, health)
        // Generate embed links from the canonical path, before compacting the clean output.
        val output = if (settings.useOfficialShortLinks) {
            detected.copy(cleanedUrl = OfficialShortLinks.shorten(detected.cleanedUrl))
        } else detected
        val embedHealth = embedUrl?.let { url ->
            val hostOnly = UrlCleaner.hostOf(url)
            if (hostOnly != null) {
                health[EmbedHealthKeys.normalize(hostOnly)] ?: EmbedHealthStatus.Unknown
            } else {
                EmbedHealthStatus.Unknown
            }
        } ?: EmbedHealthStatus.Unknown
        return PreparedLink(
            detected = output,
            embedUrl = embedUrl,
            embedHealth = embedHealth,
        )
    }

    suspend fun resolveRedirect(url: String): String? = try {
        val response = httpClient.get(url) {
            // Ktor follows redirects by default; final URL is in request/response
        }
        response.call.request.url.toString()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    suspend fun downloadMedia(
        url: String,
        settings: FukahaSettings,
        cacheDirPath: String,
    ): MediaDownloadResult {
        val cobalt = CobaltClient(
            httpClient = httpClient,
            baseUrl = settings.cobaltBaseUrl.trim().trimEnd('/'),
            apiKey = settings.cobaltApiKey,
        )
        return cobalt.download(url, cacheDirPath)
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
            followRedirects = true
        }
    }
}

data class PreparedLink(
    val detected: DetectedLink,
    val embedUrl: String?,
    val embedHealth: EmbedHealthStatus = EmbedHealthStatus.Unknown,
) {
    /**
     * URL selected by text-sharing actions. Embed intentionally falls back to
     * the cleaned URL when no compatible embedder exists.
     */
    fun textUrlFor(action: ShareAction): String? = when (action) {
        ShareAction.Ask,
        ShareAction.Download,
        -> null
        ShareAction.Clean -> detected.cleanedUrl
        ShareAction.Embed -> embedUrl ?: detected.cleanedUrl
    }
}

sealed class MediaDownloadResult {
    data class Success(
        val filePath: String,
        val mimeType: String?,
        val fileName: String,
    ) : MediaDownloadResult()

    data class Failure(val message: String) : MediaDownloadResult()
}

class CobaltClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val apiKey: String = "",
) {
    suspend fun download(url: String, cacheDirPath: String): MediaDownloadResult {
        if (baseUrl.isBlank()) {
            return MediaDownloadResult.Failure("cobalt.base_url.missing")
        }
        if (!FukahaSettings.isValidCobaltBaseUrl(baseUrl)) {
            return MediaDownloadResult.Failure("Use a valid HTTPS Cobalt URL")
        }
        return try {
            // Cobalt requires exact Accept/Content-Type values (no charset=UTF-8).
            // See https://github.com/imputnet/cobalt/blob/main/docs/api.md
            val response = httpClient.post("$baseUrl/") {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.ContentType, "application/json")
                if (apiKey.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Api-Key $apiKey")
                }
                setBody(CobaltRequest(url = url))
            }
            // Cobalt returns JSON errors with HTTP 400 for almost all failures (including auth).
            val body = runCatching { response.body<CobaltResponse>() }.getOrNull()
            when {
                body?.status == "error" || response.status.value !in 200..299 -> {
                    val code = body?.error?.code ?: body?.text
                    MediaDownloadResult.Failure(
                        code ?: "Cobalt HTTP ${response.status.value}",
                    )
                }
                body == null -> MediaDownloadResult.Failure(
                    "Cobalt HTTP ${response.status.value}",
                )
                body.status == "picker" -> {
                    val first = body.picker?.firstOrNull()
                        ?: return MediaDownloadResult.Failure("No media in picker response")
                    saveRemoteFile(first.url, cacheDirPath, first.type)
                }
                body.status == "tunnel" || body.status == "redirect" -> {
                    val mediaUrl = body.url
                        ?: return MediaDownloadResult.Failure("No media URL in Cobalt response")
                    saveRemoteFile(mediaUrl, cacheDirPath, null, body.filename)
                }
                body.status == "local-processing" -> {
                    // Best-effort: download the first tunnel stream (full client-side remux is out of scope).
                    val mediaUrl = body.tunnel?.firstOrNull()
                        ?: return MediaDownloadResult.Failure("No tunnel URL in local-processing response")
                    saveRemoteFile(mediaUrl, cacheDirPath, body.output?.type, body.output?.filename)
                }
                else -> {
                    val mediaUrl = body.url ?: body.tunnel?.firstOrNull()
                    if (mediaUrl != null) {
                        saveRemoteFile(mediaUrl, cacheDirPath, body.output?.type, body.output?.filename ?: body.filename)
                    } else {
                        MediaDownloadResult.Failure("Unexpected Cobalt status: ${body.status}")
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            MediaDownloadResult.Failure(t.message ?: "Download failed")
        }
    }

    private suspend fun saveRemoteFile(
        mediaUrl: String,
        cacheDirPath: String,
        typeHint: String?,
        fileNameHint: String? = null,
    ): MediaDownloadResult {
        val response = httpClient.get(mediaUrl)
        if (response.status.value !in 200..299) {
            return MediaDownloadResult.Failure("Media HTTP ${response.status.value}")
        }
        val bytes: ByteArray = response.body()
        if (bytes.isEmpty()) {
            return MediaDownloadResult.Failure("Empty media response")
        }
        val contentType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
        val ext = MEDIA_TYPES.entries.firstOrNull { it.value == contentType }?.key
            ?: fileNameHint?.let(::guessExtensionFromUrl)?.takeIf { it in MEDIA_TYPES }
            ?: guessExtensionFromUrl(mediaUrl)?.takeIf { it in MEDIA_TYPES }
            ?: MEDIA_TYPES.entries.firstOrNull { it.value == typeHint }?.key
            ?: when {
                typeHint?.contains("video", ignoreCase = true) == true -> "mp4"
                typeHint?.contains("audio", ignoreCase = true) == true -> "mp3"
                typeHint?.contains("image", ignoreCase = true) == true || typeHint == "photo" -> "jpg"
                else -> "bin"
            }
        val mime = MEDIA_TYPES[ext] ?: "application/octet-stream"
        val fileName = "fukaha_${PlatformClock.epochMillis()}_${kotlin.random.Random.nextLong().toULong()}.$ext"
        val path = writeCacheFile(cacheDirPath, fileName, bytes)
        return MediaDownloadResult.Success(path, mime, fileName)
    }

    private fun guessExtensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringAfterLast('/')
        val dot = path.lastIndexOf('.')
        if (dot < 0 || dot == path.lastIndex) return null
        val ext = path.substring(dot + 1).lowercase()
        return ext.takeIf { it.length in 2..5 && it.all { c -> c.isLetterOrDigit() } }
    }

    private companion object {
        val MEDIA_TYPES = linkedMapOf(
            "mp4" to "video/mp4", "webm" to "video/webm", "mov" to "video/quicktime",
            "mkv" to "video/x-matroska", "mp3" to "audio/mpeg", "m4a" to "audio/mp4",
            "ogg" to "audio/ogg", "opus" to "audio/opus", "wav" to "audio/wav",
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
            "webp" to "image/webp", "gif" to "image/gif", "avif" to "image/avif",
        )
    }
}

@Serializable
private data class CobaltRequest(
    val url: String,
    @SerialName("downloadMode") val downloadMode: String = "auto",
)

@Serializable
private data class CobaltResponse(
    val status: String? = null,
    val text: String? = null,
    val url: String? = null,
    val filename: String? = null,
    val tunnel: List<String>? = null,
    val output: CobaltOutput? = null,
    val error: CobaltError? = null,
    val picker: List<CobaltPickerItem>? = null,
)

@Serializable
private data class CobaltOutput(
    val type: String? = null,
    val filename: String? = null,
)

@Serializable
private data class CobaltError(
    val code: String? = null,
)

@Serializable
private data class CobaltPickerItem(
    val type: String? = null,
    val url: String,
)

expect object PlatformClock {
    fun epochMillis(): Long
}

expect fun writeCacheFile(cacheDirPath: String, fileName: String, bytes: ByteArray): String
