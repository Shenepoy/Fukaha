package app.fukaha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.test.*

class EmbedPreviewMediaTest {
    private val original = "https://x.com/example/status/123"
    private val embed = "https://fixvx.com/example/status/123"

    @Test fun playableMediaIsSeparateFromHtmlPlayerPagesAndWrongPosts() {
        val tags = """<head><meta property="og:title" content="A &amp; B&#x2019;s &#8212; &#x1f680;">
            <meta property="og:url" content="$original">
            <meta property="og:video:type" content="video/mp4">
            <meta property="og:video" content="https://cdn.example/video.mp4?token=a&amp;b=c">
            <meta name="twitter:image" content="https://cdn.example/poster.jpg"></head>"""
        val media = assertNotNull(EmbedPreviewChecker.classifyMetadata(original, embed, tags).preview)
        assertEquals("A & B’s — 🚀", media.title)
        assertEquals("https://cdn.example/video.mp4?token=a&b=c", media.videoUrl)
        assertEquals("https://cdn.example/poster.jpg", media.imageUrl)
        assertNull(EmbedPreviewChecker.classifyMetadata(original, embed, tags.replace("video/mp4", "text/html")).preview?.videoUrl)
        assertNull(EmbedPreviewChecker.classifyMetadata(original, embed, tags.replace(original, original.replace("123", "999"))).preview)
        val unsafe = EmbedPreviewChecker.classifyMetadata(original, embed, tags.replace("https://cdn.example", "javascript:")).preview
        assertNull(unsafe?.imageUrl)
        assertNull(unsafe?.videoUrl)
        val bilibili = "https://bilibili.com/video/BV1xx411c7mD"
        val extraQuery = tags.replace(original, "$bilibili?p=1")
        assertNotNull(EmbedPreviewChecker.classifyMetadata(bilibili, "https://vxbilibili.com/video/BV1xx411c7mD", extraQuery).preview)
    }

    @Test fun twitterOnlyCardsRemainDisplayableWithoutClaimingCanonicalVerification() {
        val result = EmbedPreviewChecker.classifyMetadata(original, embed,
            """<meta name="twitter:title" content="Game"><meta name="twitter:image" content="https://cdn.example/game.png">""")
        assertEquals(EmbedPreviewOutcome.MissingMetadata, result.outcome)
        assertEquals("https://cdn.example/game.png", assertNotNull(result.preview).imageUrl)
        assertNull(EmbedPreviewChecker.classifyMetadata(original, embed,
            """<meta property="og:title" content="A text post">""").preview)
    }

    @Test fun playerHasNoMediaSourceUntilPlayAndNeverRunsProviderHtml() {
        val html = EmbedPreviewDocument.render(EmbedPreviewMedia("</p><script>bad()</script>",
            "https://cdn.example/poster.jpg", "https://cdn.example/video.mp4?x=\"&y=<"), "Play", "Unavailable")
        assertTrue("preload=\"none\"" in html)
        assertTrue("playsinline" in html)
        assertFalse("<video src=" in html)
        assertFalse("<script>bad()" in html)
        assertTrue("data-src=\"https://cdn.example/video.mp4?x=&quot;&amp;y=&lt;\"" in html)
        assertTrue("video.removeAttribute('src')" in html)
        assertTrue("visibilitychange" in html)
        assertTrue("default-src 'none'" in html)
        assertFalse("blob:" in html)
        assertFalse("class=\"text-preview\"" in html)
        assertTrue("position:fixed;left:50vw;top:calc(var(--preview-height,100vh)/2)" in html)
        assertFalse("<p>" in html, "Media previews must not contain a caption/footer")
    }

    @Test fun directVideoPreviewDoesNotReadOrDownloadItsBody() = runTest {
        var requests = 0
        val checker = EmbedPreviewChecker(HttpClient(MockEngine {
            requests++
            // Empty body is deliberate: only video response headers are needed.
            respond("", headers = headersOf("Content-Type", "video/mp4"))
        }))
        try {
            val result = withContext(Dispatchers.Default) { checker.check(original, embed) }
            assertEquals(embed, assertNotNull(result.preview).videoUrl)
            assertEquals(1, requests)
        } finally { checker.close() }
    }

    @Test fun previewFollowsKnownProviderRedirectsButNeverAnUnrelatedDestination() = runTest {
        for ((destination, allowed) in listOf("https://embedez.com/download?q=post" to true, "https://127.0.0.1/post" to false)) {
            var requests = 0
            val checker = EmbedPreviewChecker(HttpClient(MockEngine {
                requests++
                if (requests == 1) respond("", io.ktor.http.HttpStatusCode.Found, headersOf("Location", destination))
                else respond("""<meta name="twitter:image" content="https://cdn.example/image.jpg">""", headers = headersOf("Content-Type", "text/html"))
            }) { followRedirects = false })
            try {
                val result = withContext(Dispatchers.Default) { checker.load(original, embed) }
                assertEquals(if (allowed) 2 else 1, requests)
                if (allowed) assertNotNull(result.preview) else assertEquals(EmbedPreviewOutcome.Redirected, result.outcome)
            } finally { checker.close() }
        }
    }
}
