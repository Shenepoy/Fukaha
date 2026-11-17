package app.fukaha.android.share

import android.content.Context
import android.content.ContextWrapper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Only our bounded, escaped player document is loaded; provider pages never execute here. */
@Composable
fun InlineMediaPreview(document: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true // Local Play/pause controller only, restricted by CSP.
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.mediaPlaybackRequiresUserGesture = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                }
                val owner = context.lifecycleOwner()
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        evaluateJavascript("document.querySelector('video')?.pause()", null)
                        onPause()
                    } else if (event == Lifecycle.Event.ON_START) onResume()
                }
                owner?.lifecycle?.addObserver(observer)
                setTag(app.fukaha.R.id.preview_lifecycle, Pair(owner, observer))
            }
        },
        update = { view ->
            if (view.tag != document) {
                view.tag = document
                view.loadDataWithBaseURL(null, document, "text/html", "UTF-8", null)
            }
        },
        onRelease = { view ->
            @Suppress("UNCHECKED_CAST")
            val observer = view.getTag(app.fukaha.R.id.preview_lifecycle) as? Pair<LifecycleOwner?, LifecycleEventObserver>
            observer?.first?.lifecycle?.removeObserver(observer.second)
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        },
    )
}

private fun Context.lifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.takeIf { it !== this }?.lifecycleOwner()
    else -> null
}
