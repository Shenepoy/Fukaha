package app.fukaha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.test.*

class EmbedPreviewCheckTest {
    private val original = "https://x.com/example/status/123"
    private val embed = "https://fixvx.com/example/status/123"
    private fun metadata(url: String = original) = """<head>
        <meta content="A real post" property="og:title">
        <meta property='og:url' content='$url'>
        <meta name=og:image content="https://cdn.example/image.jpg">
        </head>"""

    @Test fun matchingPostMetadataIsRequired() {
        assertEquals(EmbedPreviewOutcome.MetadataFound, EmbedPreviewChecker.classifyMetadata(original, embed, metadata()).outcome)
        for (html in listOf("<title>Domain for sale</title>", metadata("https://x.com/"), metadata("https://x.com/example/status/999"),
            "<!--${metadata()}-->", "<!--${metadata()}", "<script>${metadata()}", "<script>const sample = `${metadata()}`</script>",
            metadata().replace("https://cdn.example/image.jpg", "javascript:bad()"))) {
            assertEquals(EmbedPreviewOutcome.MissingMetadata, EmbedPreviewChecker.classifyMetadata(original, embed, html).outcome, html)
        }
    }

    @Test fun canonicalQueryAndEscapedAttributesAreMatched() {
        val url = "https://www.youtube.com/watch?v=abc&list=xyz"
        val html = metadata(url.replace("&", "&amp;"))
        assertEquals(EmbedPreviewOutcome.MetadataFound, EmbedPreviewChecker.classifyMetadata(url, "https://koutube.com/watch?v=abc&list=xyz", html).outcome)
        assertEquals(EmbedPreviewOutcome.MissingMetadata, EmbedPreviewChecker.classifyMetadata(url.replace("v=abc", "v=other"), embed, html).outcome)
    }

    @Test fun twitterTitleCanAccompanyMatchingOpenGraphMedia() {
        val original = "https://www.youtube.com/watch?v=abc"
        val html = metadata(original).replace("og:title", "twitter:title")
        assertEquals(EmbedPreviewOutcome.MetadataFound,
            EmbedPreviewChecker.classifyMetadata(original, "https://koutube.com/watch?v=abc", html).outcome)
        assertEquals(EmbedPreviewOutcome.MissingMetadata,
            EmbedPreviewChecker.classifyMetadata(original.replace("v=abc", "v=other"), embed, html).outcome)
    }

    @Test fun unrelatedRedirectCannotVerifyAPreview() {
        assertFalse(EmbedPreviewChecker.sameTarget(embed, "https://github.com/example", setOf("fixvx.com")))
        assertFalse(EmbedPreviewChecker.sameTarget(embed, "https://fixvx.com/", setOf("fixvx.com")))
        assertFalse(EmbedPreviewChecker.sameTarget(embed, "https://fixvx.com:8443/example/status/123", setOf("fixvx.com")))
        assertTrue(EmbedPreviewChecker.sameTarget(embed, embed, setOf("fixvx.com")))
    }

    @Test fun exactPostIsRequestedAndHttpErrorsAreNotVerified() = runTest {
        for ((status, outcome) in listOf(200 to EmbedPreviewOutcome.MetadataFound, 403 to EmbedPreviewOutcome.AccessBlocked,
            503 to EmbedPreviewOutcome.HttpError, 404 to EmbedPreviewOutcome.HttpError, 302 to EmbedPreviewOutcome.Redirected)) {
            val client = HttpClient(MockEngine { request ->
                assertEquals(embed, request.url.toString())
                respond(metadata(), HttpStatusCode.fromValue(status), headersOf("Content-Type", "text/html"))
            }) { followRedirects = false; expectSuccess = false }
            val checker = EmbedPreviewChecker(client)
            try {
                val result = withContext(Dispatchers.Default) { checker.check(original, embed) }
                assertEquals(outcome, result.outcome)
                assertEquals(status, result.httpStatus)
            } finally { checker.close() }
        }
    }

    @Test fun nonCatalogTargetsAreNeverContacted() = runTest {
        val checker = EmbedPreviewChecker(HttpClient(MockEngine { error("must not contact this host") }))
        try {
            assertEquals(EmbedPreviewOutcome.InvalidLink, checker.check(original, "https://example.org/post/123").outcome)
        } finally { checker.close() }
    }

    @Test fun oversizedPageDoesNotVerifyMetadataBeyondTheLimit() = runTest {
        val checker = EmbedPreviewChecker(HttpClient(MockEngine {
            respond(" ".repeat(EmbedPreviewChecker.MAX_HEAD_BYTES) + metadata(), HttpStatusCode.OK, headersOf("Content-Type", "text/html"))
        }))
        try { assertEquals(EmbedPreviewOutcome.MissingMetadata, withContext(Dispatchers.Default) { checker.check(original, embed) }.outcome) }
        finally { checker.close() }
    }

    @Test fun sourceRedirectIsUnverifiedAndHasDiagnosticEvidence() = runTest {
        val checker = EmbedHealthChecker.createWithResponsesForTests(EmbedCatalog.bundled()) { _, _ -> SourceResponse(200, "github.com") }
        val result = checker.probeWithDetail("https://fixvx.com")
        assertEquals(EmbedHealthStatus.Unknown, result.status)
        assertEquals(SourceCheckReason.Redirected, result.detail.reason)
        assertEquals("github.com", result.detail.finalHost)
    }
}
