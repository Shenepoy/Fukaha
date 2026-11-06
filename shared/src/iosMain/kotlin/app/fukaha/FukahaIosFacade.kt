package app.fukaha

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
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

    /** Use the same catalog resolution for the picker and the generated link. */
    fun selectedFixer(platformKey: String, preferredHost: String?): String? =
        bridge.effectiveService(platformKey, preferredHost)?.normalizedHost()

    fun platformKeys(): List<String> = bridge.platformKeys()

    fun serviceNames(platformKey: String): List<String> =
        bridge.servicesFor(platformKey).map { "${it.name}\t${it.normalizedHost()}" }

    fun prepare(
        text: String,
        cobaltBaseUrl: String,
        resolveShortLinks: Boolean,
        preferredFixers: Map<String, String>,
        onResult: (String?, String?, String?, String?) -> Unit,
    ) = prepareWithPreferences(text, cobaltBaseUrl, resolveShortLinks, preferredFixers, emptyList(), false, onResult)

    fun prepareWithPreferences(
        text: String,
        cobaltBaseUrl: String,
        resolveShortLinks: Boolean,
        preferredFixers: Map<String, String>,
        disabledEmbedPlatforms: List<String>,
        useOfficialShortLinks: Boolean,
        onResult: (cleanUrl: String?, embedUrl: String?, platform: String?, error: String?) -> Unit,
    ) {
        scope.launch {
            try {
                val settings = FukahaSettings(
                    cobaltBaseUrl = cobaltBaseUrl,
                    resolveShortLinks = resolveShortLinks,
                    preferredFixers = preferredFixers,
                    disabledEmbedPlatforms = disabledEmbedPlatforms.toSet(),
                    useOfficialShortLinks = useOfficialShortLinks,
                )
                val prepared = bridge.prepare(text, settings)
                ensureActive()
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                ensureActive()
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
                val result = bridge.download(url, settings, cacheDirPath)
                ensureActive()
                when (result) {
                    is MediaDownloadResult.Success ->
                        onResult(result.filePath, result.mimeType, null)
                    is MediaDownloadResult.Failure ->
                        onResult(null, null, result.message)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                ensureActive()
                onResult(null, null, t.message ?: "Download failed")
            }
        }
    }

    fun checkForUpdate(
        currentVersion: String,
        onResult: (
            status: String,
            version: String,
            changelog: String,
            htmlUrl: String,
            error: String?,
        ) -> Unit,
    ) {
        scope.launch {
            val result = AppUpdateChecker().check(currentVersion)
            ensureActive()
            when (result) {
                is UpdateCheckResult.Available -> onResult(
                    "available",
                    result.release.version,
                    result.release.changelog,
                    result.release.htmlUrl,
                    null,
                )
                is UpdateCheckResult.UpToDate -> onResult(
                    "up_to_date",
                    "",
                    "",
                    AppUpdateChecker.RELEASES_PAGE_URL,
                    null,
                )
                is UpdateCheckResult.Failed -> onResult(
                    "failed",
                    "",
                    "",
                    AppUpdateChecker.RELEASES_PAGE_URL,
                    result.message,
                )
            }
        }
    }

    fun close() {
        scope.cancel()
        bridge.close()
    }
}
