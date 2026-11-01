package app.fukaha.android.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import app.fukaha.AppRelease
import app.fukaha.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads the GitHub release APK (Kotatsu-style) and commits it through
 * [PackageInstaller], the supported replacement for ACTION_INSTALL_PACKAGE.
 */
object ApkUpdater {
    private const val APK_MIME = "application/vnd.android.package-archive"
    internal const val EXTRA_SESSION_ID = "app.fukaha.INSTALL_SESSION_ID"
    private val installations = ConcurrentHashMap<Int, CompletableDeferred<String?>>()

    internal fun completeInstall(sessionId: Int, failure: String?) {
        installations[sessionId]?.complete(failure)
    }

    fun canRequestInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        )

    suspend fun download(
        context: Context,
        release: AppRelease,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl?.takeIf { it.isNotBlank() }
            ?: error("no apk asset")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, safeApkName(release.apkName, release.version))
        val tmp = File(dest.absolutePath + ".part")
        dest.delete()
        tmp.delete()
        val client = HttpClient(OkHttp) {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout) {
                requestTimeoutMillis = 5 * 60 * 1000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 5 * 60 * 1000
            }
        }
        var succeeded = false
        try {
            val response = client.get(url) {
                header(
                    HttpHeaders.UserAgent,
                    "Fukaha/${BuildConfig.VERSION_NAME} (+https://github.com/Zyzto/Fukaha)",
                )
                header(HttpHeaders.Accept, APK_MIME)
            }
            if (!response.status.isSuccess()) {
                error("http ${response.status.value}")
            }
            val total = response.contentLength()?.takeIf { it > 0 }
                ?: release.apkSizeBytes?.takeIf { it > 0 }
                ?: -1L
            val channel = response.bodyAsChannel()
            tmp.outputStream().use { out ->
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                var lastEmitted = -1f
                while (!channel.isClosedForRead) {
                    val n = channel.readAvailable(buf, 0, buf.size)
                    if (n < 0) break
                    if (n == 0) continue
                    out.write(buf, 0, n)
                    copied += n
                    if (total > 0L) {
                        val progress = (copied.toFloat() / total).coerceIn(0f, 1f)
                        if (progress - lastEmitted >= 0.02f || progress == 1f) {
                            lastEmitted = progress
                            withContext(Dispatchers.Main.immediate) { onProgress(progress) }
                        }
                    }
                }
            }
            if (tmp.length() <= 0L) error("empty apk")
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            succeeded = true
            dest
        } finally {
            client.close()
            if (!succeeded) tmp.delete()
        }
    }

    suspend fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(
                    PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED,
                )
            }
        }
        val sessionId = installer.createSession(params)
        val result = CompletableDeferred<String?>()
        installations[sessionId] = result
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pending = PendingIntent.getActivity(
                    context,
                    sessionId,
                    Intent(context, ApkInstallActivity::class.java)
                        .setAction(ApkInstallActivity.ACTION_STATUS)
                        .putExtra(EXTRA_SESSION_ID, sessionId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    flags,
                )
                session.commit(pending.intentSender)
                committed = true
            }
            result.await()?.let { error(it) }
        } catch (t: Throwable) {
            if (!committed) {
                runCatching { installer.abandonSession(sessionId) }
            }
            throw t
        } finally {
            installations.remove(sessionId)
            result.cancel()
        }
    }

    internal fun safeApkName(raw: String?, version: String): String {
        val fallback = "fukaha-$version.apk"
        val trimmed = raw?.substringAfterLast('/')?.ifBlank { fallback } ?: fallback
        val safe = trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (safe.endsWith(".apk", ignoreCase = true)) safe else "$safe.apk"
    }
}
