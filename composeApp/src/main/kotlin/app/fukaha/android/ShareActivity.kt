package app.fukaha.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import app.fukaha.FukahaApp
import app.fukaha.FukahaSettings
import app.fukaha.MediaDownloadResult
import app.fukaha.PreparedLink
import app.fukaha.R
import app.fukaha.ShareAction
import app.fukaha.android.share.AutoActionProgress
import app.fukaha.android.share.ShareSheet
import app.fukaha.android.theme.FukahaTheme
import app.fukaha.fukaha
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FORCE_ASK = "app.fukaha.FORCE_ASK"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedText = intent.extractSharedText()
        val forceAsk = intent.getBooleanExtra(EXTRA_FORCE_ASK, false)
        val app = application.fukaha()

        setContent {
            var settings by remember { mutableStateOf(FukahaSettings()) }
            var prepared by remember { mutableStateOf<PreparedLink?>(null) }
            var loading by remember { mutableStateOf(true) }
            var downloading by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            // After auto Download fails, drop into the share sheet (no URL fallback).
            var showSheetAfterAutoFail by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(sharedText) {
                settings = app.settingsStore.get()
                if (settings.deleteCacheAfterShare) {
                    withContext(Dispatchers.IO) { MediaCache.deleteExpired(mediaCacheDir()) }
                }
                if (sharedText.isNullOrBlank()) {
                    error = getString(R.string.no_link)
                    loading = false
                    return@LaunchedEffect
                }
                prepared = withContext(Dispatchers.IO) {
                    val health = app.healthStore.get().statuses
                    app.bridge.prepare(sharedText, settings, health)
                }
                if (prepared == null) {
                    error = getString(R.string.no_link)
                    loading = false
                    return@LaunchedEffect
                }
                loading = false

                val action = if (forceAsk) ShareAction.Ask else settings.effectiveDefaultAction()
                when (action) {
                    ShareAction.Ask -> Unit
                    ShareAction.Clean -> {
                        shareText(prepared!!.textUrlFor(action)!!)
                        finish()
                    }
                    ShareAction.Embed -> {
                        shareText(prepared!!.textUrlFor(action)!!)
                        finish()
                    }
                    ShareAction.Download -> {
                        downloading = true
                        val shared = downloadAndShare(app, prepared!!, settings)
                        downloading = false
                        if (shared) finish() else showSheetAfterAutoFail = true
                    }
                }
            }

            FukahaTheme(theme = settings.theme) {
                val autoAction = settings.effectiveDefaultAction()
                if (
                    !forceAsk &&
                    autoAction != ShareAction.Ask &&
                    error == null &&
                    !showSheetAfterAutoFail
                ) {
                    AutoActionProgress()
                } else {
                    ShareSheet(
                        loading = loading,
                        downloading = downloading,
                        error = error,
                        prepared = prepared,
                        mediaDownloadEnabled = settings.hasValidCobaltBaseUrl,
                        onDismiss = { finish() },
                        onShareCleaned = {
                            prepared?.let {
                                shareText(it.textUrlFor(ShareAction.Clean)!!)
                                finish()
                            }
                        },
                        onShareEmbed = {
                            prepared?.let {
                                shareText(it.textUrlFor(ShareAction.Embed)!!)
                                finish()
                            }
                        },
                        onShareMedia = {
                            val link = prepared ?: return@ShareSheet
                            scope.launch {
                                downloading = true
                                val shared = downloadAndShare(app, link, settings)
                                downloading = false
                                // On failure the sheet stays open so the error is visible;
                                // never silently share an embed URL after a media-file tap.
                                if (shared) finish()
                            }
                        },
                        onCopyOriginal = {
                            prepared?.detected?.originalUrl?.let { copyText(it) }
                        },
                        onCopyCleaned = {
                            prepared?.detected?.cleanedUrl?.let { copyText(it) }
                        },
                        onCopyEmbed = {
                            prepared?.embedUrl?.let { copyText(it) }
                        },
                    )
                }
            }
        }
    }

    /**
     * This activity is `singleInstance`, so a second share (or a paste from Settings)
     * reuses the existing instance. Rebuild it so the sheet reflects the new link
     * instead of the one captured in [onCreate].
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    /**
     * Downloads the media behind [link] and hands it to the system chooser.
     * Returns false when the download failed, leaving the caller to decide whether
     * to fall back to the share sheet or keep it open.
     */
    private suspend fun downloadAndShare(
        app: FukahaApp,
        link: PreparedLink,
        settings: FukahaSettings,
    ): Boolean {
        val result = withContext(Dispatchers.IO) {
            app.bridge.download(
                link.detected.cleanedUrl,
                settings,
                mediaCacheDir().absolutePath,
            )
        }
        return when (result) {
            is MediaDownloadResult.Success -> {
                shareFile(result.filePath, result.mimeType)
                true
            }
            is MediaDownloadResult.Failure -> {
                Toast.makeText(
                    this,
                    downloadFailureMessage(result.message),
                    Toast.LENGTH_LONG,
                ).show()
                false
            }
        }
    }

    private fun mediaCacheDir(): File {
        val dir = File(cacheDir, "fukaha")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun downloadFailureMessage(raw: String): String {
        val lower = raw.lowercase()
        val looksLikeAuth = "auth" in lower || "jwt" in lower ||
            "api.key" in lower || "api-key" in lower || "turnstile" in lower ||
            "base_url" in lower
        return if (looksLikeAuth) {
            getString(R.string.cobalt_auth_required)
        } else {
            "${getString(R.string.download_failed)}: $raw"
        }
    }

    private fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.app_name)))
    }

    private fun shareFile(path: String, mimeType: String?) {
        val file = File(path)
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", uri)
        }
        startActivity(Intent.createChooser(send, getString(R.string.app_name)))
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("fukaha", text))
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
}

private fun Intent.extractSharedText(): String? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    return getStringExtra(Intent.EXTRA_TEXT)
        ?: getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        ?: getStringExtra(Intent.EXTRA_SUBJECT)
}
