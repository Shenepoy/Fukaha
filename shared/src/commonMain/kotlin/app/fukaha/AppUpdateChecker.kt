package app.fukaha

import kotlinx.coroutines.CancellationException

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Checks GitHub Releases for a newer tag — the usual FOSS path when the
 * app is not shipped through Play (NewPipe, Seal, Kizzy, Obtainium).
 */
class AppUpdateChecker(
    private val httpClient: HttpClient? = null,
    private val latestUrl: String = LATEST_RELEASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(currentVersion: String): UpdateCheckResult {
        val client = httpClient ?: defaultHttpClient()
        return try {
            val response = client.get(latestUrl) {
                header(
                    HttpHeaders.UserAgent,
                    "Fukaha/$currentVersion (+https://github.com/Zyzto/Fukaha)",
                )
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
            if (response.status.value != 200) {
                return UpdateCheckResult.Failed("http ${response.status.value}")
            }
            evaluate(json.decodeFromString(response.bodyAsText()), currentVersion)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            UpdateCheckResult.Failed(t.message ?: "update.check.failed")
        } finally {
            if (httpClient == null) client.close()
        }
    }

    companion object {
        const val OWNER = "Zyzto"
        const val REPO = "Fukaha"
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Zyzto/Fukaha/releases/latest"
        const val RELEASES_PAGE_URL =
            "https://github.com/Zyzto/Fukaha/releases/latest"

        internal fun evaluate(
            dto: GithubReleaseDto,
            currentVersion: String,
        ): UpdateCheckResult {
            if (dto.draft || dto.tagName.isBlank()) return UpdateCheckResult.UpToDate
            val version = AppVersion.normalize(dto.tagName)
            val apk = GithubApkAsset.pick(dto.assets)
            val release = AppRelease(
                version = version,
                tagName = dto.tagName,
                title = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName,
                changelog = ChangelogFormatter.displayNotes(dto.body),
                htmlUrl = dto.htmlUrl.ifBlank { RELEASES_PAGE_URL },
                publishedAt = dto.publishedAt,
                apkUrl = apk?.browserDownloadUrl,
                apkName = apk?.name,
                apkSizeBytes = apk?.size?.takeIf { it > 0L },
            )
            return if (AppVersion.isNewer(version, currentVersion)) {
                UpdateCheckResult.Available(release)
            } else {
                UpdateCheckResult.UpToDate
            }
        }

        fun defaultHttpClient(): HttpClient = HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 15_000
            }
        }
    }
}

@Serializable
internal data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
internal data class GithubAssetDto(
    val name: String = "",
    val size: Long = 0,
    @SerialName("content_type") val contentType: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

internal object GithubApkAsset {
    private const val APK_MIME = "application/vnd.android.package-archive"

    fun pick(assets: List<GithubAssetDto>): GithubAssetDto? {
        val apks = assets.filter { it.isApk() && it.browserDownloadUrl.isNotBlank() }
        return apks.firstOrNull { it.name.startsWith("fukaha", ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    private fun GithubAssetDto.isApk(): Boolean =
        contentType.equals(APK_MIME, ignoreCase = true) ||
            name.endsWith(".apk", ignoreCase = true)
}
