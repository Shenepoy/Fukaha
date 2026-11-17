package app.fukaha

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Evidence about this exact link. MetadataFound is not a guarantee of a chat client's preview. */
enum class EmbedPreviewOutcome {
    MetadataFound, MissingMetadata, Redirected, AccessBlocked, HttpError, TimedOut,
    NetworkError, UnsupportedContent, InvalidLink,
}

data class EmbedPreviewResult(
    val outcome: EmbedPreviewOutcome,
    val httpStatus: Int? = null,
    val preview: EmbedPreviewMedia? = null,
)

data class EmbedPreviewMedia(val title: String, val imageUrl: String? = null, val videoUrl: String? = null)

/** Opt-in check of a prepared catalog link. Never probes arbitrary hosts or changes a selection. */
class EmbedPreviewChecker(private val client: HttpClient = previewHttpClient()) {
    suspend fun check(originalUrl: String, embedUrl: String, catalog: EmbedCatalog = EmbedCatalog.bundled()): EmbedPreviewResult =
        request(originalUrl, embedUrl, embedUrl, catalog, 0, false)

    /** Render previews through a bounded set of observed provider redirects, never arbitrary pages. */
    suspend fun load(originalUrl: String, embedUrl: String): EmbedPreviewResult =
        request(originalUrl, embedUrl, embedUrl, EmbedCatalog.bundled(), 4, true)

    private suspend fun request(originalUrl: String, embedUrl: String, targetUrl: String, catalog: EmbedCatalog,
                                redirectsLeft: Int, renderPreview: Boolean): EmbedPreviewResult {
        val allowedHosts = allowedHosts(originalUrl, embedUrl, catalog)
            ?: return EmbedPreviewResult(EmbedPreviewOutcome.InvalidLink)
        return try {
            withTimeoutOrNull(EmbedHealthPolicy.PROBE_TIMEOUT_MS * 2) {
                client.prepareGet(targetUrl) {
                    header("Accept", "text/html,application/xhtml+xml")
                    if (renderPreview) {
                        header("User-Agent", "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)")
                        header("Range", "bytes=0-131071")
                    }
                }.execute { response ->
                    val status = response.status.value
                    val finalUrl = response.call.request.url.toString()
                    when {
                        status == 401 || status == 403 || status == 429 -> EmbedPreviewResult(EmbedPreviewOutcome.AccessBlocked, status)
                        status in 300..399 -> {
                            val next = response.headers["Location"]?.let { location ->
                                runCatching { URLBuilder(targetUrl).takeFrom(location).buildString() }.getOrNull()
                            }
                            if (renderPreview && redirectsLeft > 0 && next != null && previewRedirectAllowed(next, catalog))
                                request(originalUrl, embedUrl, next, catalog, redirectsLeft - 1, true)
                            else EmbedPreviewResult(EmbedPreviewOutcome.Redirected, status)
                        }
                        status !in 200..299 -> EmbedPreviewResult(EmbedPreviewOutcome.HttpError, status)
                        !renderPreview && !sameTarget(embedUrl, finalUrl, allowedHosts) -> EmbedPreviewResult(EmbedPreviewOutcome.Redirected, status)
                        response.headers["Content-Type"]?.substringBefore(';')?.lowercase()?.startsWith("video/") == true ->
                            // Headers are enough. Never consume the video body to build a preview.
                            EmbedPreviewResult(EmbedPreviewOutcome.UnsupportedContent, status,
                                EmbedPreviewMedia("", videoUrl = targetUrl))
                        response.headers["Content-Type"]?.substringBefore(';')?.lowercase() !in HTML_TYPES ->
                            EmbedPreviewResult(EmbedPreviewOutcome.UnsupportedContent, status)
                        else -> {
                            // Read only a bounded head prefix; do not buffer a whole page or media file.
                            val channel = response.bodyAsChannel()
                            val bytes = ByteArray(MAX_HEAD_BYTES)
                            var size = 0
                            while (size < bytes.size) {
                                val count = channel.readAvailable(bytes, size, minOf(8192, bytes.size - size))
                                if (count < 0) break
                                size += count
                                if (bytes.decodeToString(0, size).contains("</head>", ignoreCase = true)) break
                            }
                            classifyMetadata(originalUrl, embedUrl, bytes.decodeToString(0, size), status)
                        }
                    }
                }
            } ?: EmbedPreviewResult(EmbedPreviewOutcome.TimedOut)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            EmbedPreviewResult(if (error::class.simpleName.orEmpty().contains("Timeout"))
                EmbedPreviewOutcome.TimedOut else EmbedPreviewOutcome.NetworkError)
        }
    }

    fun close() = client.close()

    companion object {
        private fun previewHttpClient() = HttpClient {
            expectSuccess = false
            // Do not send the post URL on to an unrelated destination or login page.
            followRedirects = false
            install(HttpTimeout) {
                requestTimeoutMillis = EmbedHealthPolicy.PROBE_TIMEOUT_MS * 2
                connectTimeoutMillis = EmbedHealthPolicy.PROBE_TIMEOUT_MS
                socketTimeoutMillis = EmbedHealthPolicy.PROBE_TIMEOUT_MS
            }
        }

        const val MAX_HEAD_BYTES = 131_072
        private val HTML_TYPES = setOf("text/html", "application/xhtml+xml")

        private fun previewRedirectAllowed(value: String, catalog: EmbedCatalog): Boolean = runCatching {
            val url = Url(value)
            val hosts = catalog.platformKeys().flatMap { catalog.activeServices(it) }
                .flatMap { it.alternateHosts + it.normalizedHost() }.map { Url(it).host.lowercase() }.toSet()
            url.protocol.name == "https" && url.port == 443 && url.user.isNullOrEmpty() && url.password.isNullOrEmpty() &&
                (url.host.lowercase() in hosts || url.host.lowercase() in setOf("embedez.com", "vxtwitter.com") ||
                    url.host.lowercase().endsWith(".tiktokcdn-us.com"))
        }.getOrDefault(false)
        private val META = Regex("""<meta\b(?:[^>"']|"[^"]*"|'[^']*')*>""", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE = Regex("""([:\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
        private val NON_MARKUP = Regex("""<!--[\s\S]*?(?:-->|$)|<(script|style)\b[^>]*>[\s\S]*?(?:</\1\s*>|$)""", RegexOption.IGNORE_CASE)

        fun allowedHosts(original: String, embed: String, catalog: EmbedCatalog = EmbedCatalog.bundled()): Set<String>? {
            val platform = catalog.detectPlatformKey(original) ?: return null
            val target = runCatching { Url(embed) }.getOrNull() ?: return null
            if (target.protocol.name != "https" || target.port != 443 || !target.user.isNullOrEmpty() || !target.password.isNullOrEmpty()) return null
            val service = catalog.serviceForHost(platform, "https://${target.host}") ?: return null
            return (service.alternateHosts + service.normalizedHost()).mapNotNull { host ->
                runCatching { Url(host).host.lowercase() }.getOrNull()
            }.toSet()
        }

        fun sameTarget(requested: String, finalUrl: String, allowedHosts: Set<String>): Boolean = runCatching {
            val end = Url(finalUrl)
            end.protocol.name == "https" && end.port == 443 && end.user.isNullOrEmpty() && end.password.isNullOrEmpty() &&
                end.host.lowercase() in allowedHosts && identity(requested) == identity(finalUrl)
        }.getOrDefault(false)

        private fun identity(url: String): Pair<String, List<Pair<String, List<String>>>> {
            val parsed = Url(UrlCleaner.clean(url))
            return parsed.encodedPath.trimEnd('/') to parsed.parameters.entries()
                .sortedBy { it.key }.map { (key, values) -> key to values }
        }

        fun classifyMetadata(original: String, embed: String, html: String, status: Int = 200): EmbedPreviewResult {
            // Content may echo HTML examples in comments/scripts; those are not preview tags.
            val markup = NON_MARKUP.replace(html.take(MAX_HEAD_BYTES), "")
            val headEnd = markup.indexOf("</head>", ignoreCase = true)
            val head = if (headEnd >= 0) markup.substring(0, headEnd) else markup
            val properties = META.findAll(head).mapNotNull { tag ->
                val attrs = ATTRIBUTE.findAll(tag.value).associate { attr ->
                    attr.groupValues[1].lowercase() to decodeEntities(attr.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty())
                }
                val key = attrs["property"] ?: attrs["name"] ?: return@mapNotNull null
                attrs["content"]?.trim()?.takeIf { it.isNotEmpty() }?.let { key.lowercase() to it }
            }.groupBy({ it.first }, { it.second })
            val canonical = properties["og:url"].orEmpty().any { candidate ->
                runCatching {
                    val url = Url(candidate)
                    val host = UrlCleaner.hostOf(candidate)
                    val originalHost = UrlCleaner.hostOf(original)
                    val embedHost = UrlCleaner.hostOf(embed)
                    val samePlatform = EmbedCatalog.bundled().detectPlatformKey(candidate)?.let {
                        it == EmbedCatalog.bundled().detectPlatformKey(original)
                    } == true
                    url.protocol.name == "https" && url.user.isNullOrEmpty() && url.password.isNullOrEmpty() &&
                        (host == originalHost || host == embedHost || samePlatform) &&
                        (identity(candidate) == identity(original) || identity(candidate) == identity(embed))
                }.getOrDefault(false)
            }
            val media = listOf("og:image", "og:image:url", "og:video", "og:video:url", "og:video:secure_url")
                .flatMap { properties[it].orEmpty() }.any { value ->
                    runCatching { Url(value).let { it.protocol.name == "https" && it.host.isNotBlank() && it.user.isNullOrEmpty() && it.password.isNullOrEmpty() } }.getOrDefault(false)
                }
            // Some catalog services (including Koutube) mix Twitter titles with OG media.
            val title = !properties["og:title"].isNullOrEmpty() || !properties["twitter:title"].isNullOrEmpty()
            val found = canonical && media && title
            fun firstUrl(vararg keys: String): String? = keys.asSequence()
                .flatMap { properties[it].orEmpty().asSequence() }.firstOrNull { safeMediaUrl(it) }
            val image = firstUrl("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src")
            // og:video can be an HTML player, not a stream. Never load a third-party page as video.
            val videoType = properties["og:video:type"]?.firstOrNull()?.lowercase()
            val video = if (videoType == null || videoType.startsWith("video/") || videoType in setOf("application/x-mpegurl", "application/vnd.apple.mpegurl"))
                firstUrl("og:video:secure_url", "og:video", "og:video:url", "twitter:player:stream") else null
            val wrongPost = properties["og:url"].orEmpty().any { candidate ->
                runCatching {
                    val originalKey = previewPostKey(original)
                    val candidateKey = previewPostKey(candidate)
                    EmbedCatalog.bundled().detectPlatformKey(candidate) == EmbedCatalog.bundled().detectPlatformKey(original) &&
                        originalKey != null && candidateKey != null && originalKey != candidateKey
                }.getOrDefault(false)
            }
            val preview = if (!wrongPost && (image != null || video != null))
                EmbedPreviewMedia((properties["og:title"] ?: properties["twitter:title"])?.firstOrNull().orEmpty().take(500), image, video) else null
            return EmbedPreviewResult(if (found) EmbedPreviewOutcome.MetadataFound else EmbedPreviewOutcome.MissingMetadata, status, preview)
        }

        private fun safeMediaUrl(value: String): Boolean = runCatching {
            Url(value).let { it.protocol.name == "https" && it.port == 443 && it.host.isNotBlank() &&
                it.user.isNullOrEmpty() && it.password.isNullOrEmpty() }
        }.getOrDefault(false)

        // Display tolerates canonical slugs and harmless query changes. Verification above
        // remains stricter. Only suppress a card when both URLs identify different posts.
        private fun previewPostKey(value: String): String? = runCatching {
            val url = Url(value)
            Regex("/(?:status|reel|reels|p|video|comments|pin|games|artworks|post|spotlight)/([^/]+)", RegexOption.IGNORE_CASE)
                .find(url.encodedPath)?.groupValues?.get(1)
                ?: Regex("/([0-9]{8,})(?:/|$)").find(url.encodedPath)?.groupValues?.get(1)
                ?: url.parameters["v"]
        }.getOrNull()

        private fun decodeEntities(value: String): String = value
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .let { decoded -> Regex("&#(x[0-9a-f]+|[0-9]+);", RegexOption.IGNORE_CASE).replace(decoded) { match ->
                val number = match.groupValues[1]
                val point = if (number.startsWith("x", true)) number.drop(1).toIntOrNull(16) else number.toIntOrNull()
                when {
                    point == null || point !in 1..0x10ffff || point in 0xd800..0xdfff -> match.value
                    point <= 0xffff -> point.toChar().toString()
                    else -> buildString { append(((point - 0x10000) / 1024 + 0xd800).toChar()); append(((point - 0x10000) % 1024 + 0xdc00).toChar()) }
                }
            } }
    }
}
