package app.fukaha.web

import app.fukaha.EmbedPreviewOutcome
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import kotlin.js.Promise
import kotlin.test.*

class WebPreviewCheckerTest {
    private val original = "https://x.com/example/status/123"
    private val embed = "https://fixvx.com/example/status/123"
    private val metadata = """<head><meta property="og:title" content="Post"><meta property="og:url" content="https://x.com/example/status/123"><meta property="og:image" content="https://cdn.example/image.jpg"></head>"""

    private fun response(body: String, status: Int = 200, finalUrl: String = embed): dynamic {
        val create: dynamic = js("(function(body, status, url) { var r = new Response(body, {status:status, headers:{'Content-Type':'text/html'}}); Object.defineProperty(r, 'url', {value:url}); return r; })")
        return create(body, status, finalUrl)
    }

    @Test fun readablePostMetadataCanBeVerifiedButHttpErrorsCannot() = MainScope().promise {
        val originalFetch = window.asDynamic().fetch
        try {
            for ((status, expected) in listOf(200 to EmbedPreviewOutcome.MetadataFound, 403 to EmbedPreviewOutcome.AccessBlocked, 503 to EmbedPreviewOutcome.HttpError)) {
                window.asDynamic().fetch = { url: dynamic, options: dynamic ->
                    assertEquals(embed, url as String)
                    assertEquals("cors", options.mode as String)
                    assertEquals("omit", options.credentials as String)
                    Promise.resolve<dynamic>(response(metadata, status))
                }
                assertEquals(expected, WebPreviewChecker.check(original, embed).outcome)
            }
        } finally { window.asDynamic().fetch = originalFetch }
    }

    @Test fun opaqueAndBlockedResponsesAreNeverVerified() = MainScope().promise {
        val originalFetch = window.asDynamic().fetch
        try {
            window.asDynamic().fetch = { _: dynamic, _: dynamic -> Promise.resolve<dynamic>(js("({type:'opaque',status:0})")) }
            assertEquals(EmbedPreviewOutcome.NetworkError, WebPreviewChecker.check(original, embed).outcome)
            window.asDynamic().fetch = { _: dynamic, _: dynamic -> Promise.reject(Throwable("CORS")) }
            assertEquals(EmbedPreviewOutcome.NetworkError, WebPreviewChecker.check(original, embed).outcome)
        } finally { window.asDynamic().fetch = originalFetch }
    }

    @Test fun wrongPostRedirectAndParkedPagesRemainUnverified() = MainScope().promise {
        val originalFetch = window.asDynamic().fetch
        try {
            for ((body, url, expected) in listOf(
                Triple(metadata.replace("status/123", "status/999"), embed, EmbedPreviewOutcome.MissingMetadata),
                Triple("<title>Domain for sale</title>", embed, EmbedPreviewOutcome.MissingMetadata),
                Triple(metadata, "https://github.com/example", EmbedPreviewOutcome.Redirected),
                Triple(" ".repeat(131_072) + metadata, embed, EmbedPreviewOutcome.MissingMetadata),
            )) {
                window.asDynamic().fetch = { _: dynamic, _: dynamic -> Promise.resolve<dynamic>(response(body, finalUrl = url)) }
                assertEquals(expected, WebPreviewChecker.check(original, embed).outcome)
            }
        } finally { window.asDynamic().fetch = originalFetch }
    }
}
