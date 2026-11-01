package app.fukaha.web

import app.fukaha.EmbedPreviewChecker
import app.fukaha.EmbedPreviewOutcome
import app.fukaha.EmbedPreviewResult
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlin.js.Promise

/** Readable CORS responses only. An opaque response cannot verify preview metadata. */
object WebPreviewChecker {
    suspend fun check(original: String, embed: String): EmbedPreviewResult {
        val allowedHosts = EmbedPreviewChecker.allowedHosts(original, embed)
            ?: return EmbedPreviewResult(EmbedPreviewOutcome.InvalidLink)
        val controller: dynamic = js("new AbortController()")
        var timedOut = false
        val timer = window.setTimeout({ timedOut = true; controller.abort() }, 10_000)
        val init: dynamic = js("({method:'GET',mode:'cors',cache:'no-store',credentials:'omit',redirect:'error',referrerPolicy:'no-referrer',headers:{Accept:'text/html,application/xhtml+xml'}})")
        init.signal = controller.signal
        return try {
            val response: dynamic = (window.asDynamic().fetch(embed, init) as Promise<dynamic>).await()
            if (response.type == "opaque" || response.type == "opaqueredirect") return EmbedPreviewResult(EmbedPreviewOutcome.NetworkError)
            val status = (response.status as Number).toInt()
            if (status == 401 || status == 403 || status == 429) return EmbedPreviewResult(EmbedPreviewOutcome.AccessBlocked, status)
            if (status in 300..399) return EmbedPreviewResult(EmbedPreviewOutcome.Redirected, status)
            if (status !in 200..299) return EmbedPreviewResult(EmbedPreviewOutcome.HttpError, status)
            if (!EmbedPreviewChecker.sameTarget(embed, response.url as String, allowedHosts)) {
                return EmbedPreviewResult(EmbedPreviewOutcome.Redirected, status)
            }
            val contentType = (response.headers.get("Content-Type") as? String)?.substringBefore(';')?.lowercase()
            if (contentType !in setOf("text/html", "application/xhtml+xml") || response.body == null) {
                return EmbedPreviewResult(EmbedPreviewOutcome.UnsupportedContent, status)
            }
            val reader = response.body.getReader()
            val decoder: dynamic = js("new TextDecoder('utf-8')")
            val options: dynamic = js("({stream:true})")
            val html = StringBuilder()
            var bytesRead = 0
            while (bytesRead < EmbedPreviewChecker.MAX_HEAD_BYTES) {
                val chunk = (reader.read() as Promise<dynamic>).await()
                if (chunk.done == true) break
                val remaining = EmbedPreviewChecker.MAX_HEAD_BYTES - bytesRead
                val bytes = chunk.value.subarray(0, remaining)
                bytesRead += (bytes.byteLength as Number).toInt()
                html.append(decoder.decode(bytes, options) as String)
                if (html.contains("</head>", ignoreCase = true)) break
            }
            html.append(decoder.decode() as String)
            EmbedPreviewChecker.classifyMetadata(original, embed, html.toString(), status)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            EmbedPreviewResult(if (timedOut) EmbedPreviewOutcome.TimedOut else EmbedPreviewOutcome.NetworkError)
        } finally {
            window.clearTimeout(timer)
            controller.abort()
        }
    }
}
