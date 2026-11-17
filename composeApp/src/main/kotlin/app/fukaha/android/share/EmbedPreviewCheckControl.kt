package app.fukaha.android.share

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.fukaha.EmbedPreviewChecker
import app.fukaha.EmbedPreviewOutcome
import app.fukaha.EmbedPreviewResult
import app.fukaha.EmbedPreviewDocument
import app.fukaha.R
import kotlinx.coroutines.launch

@Composable
fun EmbedPreviewCheckControl(original: String, embed: String) {
    val checker = remember { EmbedPreviewChecker() }
    DisposableEffect(checker) { onDispose { checker.close() } }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<EmbedPreviewResult?>(null) }
    LaunchedEffect(original, embed) {
        checking = true
        result = null
        try { result = checker.load(original, embed) }
        finally { checking = false }
    }
    Column {
        TextButton(enabled = !checking, onClick = {
            if (result?.preview != null) {
                result = null
            } else {
                checking = true
                result = null
                scope.launch {
                    try { result = checker.load(original, embed) }
                    finally { checking = false }
                }
            }
        }) {
            Text(stringResource(when {
                result?.preview != null -> R.string.preview_close
                checking -> R.string.checking_preview
                result != null -> R.string.preview_retry
                else -> R.string.check_preview
            }))
        }
        val current = result
        current?.preview?.let { media ->
            InlineMediaPreview(EmbedPreviewDocument.render(media,
                stringResource(R.string.preview_play), stringResource(R.string.preview_playback_error)))
        }
        if (current?.preview == null) {
            val text = if (current == null) stringResource(R.string.preview_check_hint) else {
                val resource = when (current.outcome) {
                    EmbedPreviewOutcome.MetadataFound -> R.string.preview_found
                    EmbedPreviewOutcome.MissingMetadata -> R.string.preview_missing
                    EmbedPreviewOutcome.Redirected -> R.string.preview_redirected
                    EmbedPreviewOutcome.AccessBlocked -> R.string.preview_blocked
                    EmbedPreviewOutcome.HttpError -> R.string.preview_http_error
                    EmbedPreviewOutcome.TimedOut -> R.string.preview_timed_out
                    EmbedPreviewOutcome.NetworkError -> R.string.preview_network_error
                    EmbedPreviewOutcome.UnsupportedContent -> R.string.preview_unsupported
                    EmbedPreviewOutcome.InvalidLink -> R.string.preview_invalid
                }
                stringResource(resource) + (current.httpStatus?.let { " (HTTP $it)" } ?: "")
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
