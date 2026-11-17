package app.fukaha

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Callback-friendly API for Swift Share Extension (no SKIE required).
 */
class FukahaIosFacade {
    private val bridge = FukahaBridge()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun extractUrl(text: String): String? = bridge.extractUrl(text)

    fun clean(url: String): String = bridge.clean(url)

    fun detectPlatform(url: String): String? = bridge.detectPlatform(url)

    fun embed(url: String, preferredFixerHost: String?): String? =
        bridge.embed(url, preferredFixerHost)

    fun defaultFixer(platformKey: String): String? = bridge.defaultFixer(platformKey)

    fun platformKeys(): List<String> = bridge.platformKeys()

    fun serviceNames(platformKey: String): List<String> =
        bridge.servicesFor(platformKey).map { "${it.name}\t${it.normalizedHost()}" }

    fun prepare(
        text: String,
        cobaltBaseUrl: String,
        resolveShortLinks: Boolean,
        preferredFixerHost: String?,
        onResult: (cleanUrl: String?, embedUrl: String?, platform: String?, error: String?) -> Unit,
    ) {
        scope.launch {
            try {
                val settings = FukahaSettings(
                    cobaltBaseUrl = cobaltBaseUrl,
                    resolveShortLinks = resolveShortLinks,
                    preferredFixers = preferredFixerHost?.let { host ->
                        bridge.detectPlatform(UrlCleaner.extractFirstUrl(text) ?: text)
                            ?.let { key -> mapOf(key to host) }
                    } ?: emptyMap(),
                )
                val prepared = bridge.prepare(text, settings)
                if (prepared == null) {
                    onResult(null, null, null, "No link found")
                } else {
                    onResult(
                        prepared.detected.cleanedUrl,
                        prepared.embedUrl,
                        prepared.detected.platformKey,
                        null,
                    )
                }
            } catch (t: Throwable) {
                onResult(null, null, null, t.message ?: "Prepare failed")
            }
        }
    }

    fun download(
        url: String,
        cobaltBaseUrl: String,
        cobaltApiKey: String,
        cacheDirPath: String,
        onResult: (filePath: String?, mimeType: String?, error: String?) -> Unit,
    ) {
        scope.launch {
            try {
                val settings = FukahaSettings(
                    cobaltBaseUrl = cobaltBaseUrl,
                    cobaltApiKey = cobaltApiKey,
                )
                when (val result = bridge.download(url, settings, cacheDirPath)) {
                    is MediaDownloadResult.Success ->
                        onResult(result.filePath, result.mimeType, null)
                    is MediaDownloadResult.Failure ->
                        onResult(null, null, result.message)
                }
            } catch (t: Throwable) {
                onResult(null, null, t.message ?: "Download failed")
            }
        }
    }

    fun close() {
        bridge.close()
    }
}
