package app.fukaha

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlSafetyTest {
    @Test
    fun extractsBalancedParenthesesAndApostrophes() {
        val wiki = "https://en.wikipedia.org/wiki/Function_(mathematics)"
        assertEquals(wiki, UrlCleaner.extractFirstUrl("See ($wiki)."))
        assertEquals("https://example.com/O'Brien", UrlCleaner.extractFirstUrl("https://example.com/O'Brien"))
        assertEquals("https://example.com/a", UrlCleaner.extractFirstUrl("<a href='https://example.com/a'>open</a>"))
    }

    @Test
    fun preservesFunctionalQueriesAndTimestamps() {
        assertEquals("https://example.com?s=kotlin&source=docs&ref=chapter&t=10", UrlCleaner.clean(
            "https://example.com/?s=kotlin&source=docs&ref=chapter&t=10&utm_source=share",
        ))
        assertEquals("https://youtube.com/watch?v=abc&t=90", UrlCleaner.clean(
            "https://youtube.com/watch?v=abc&t=90&si=tracker",
        ))
        assertEquals("https://x.com/user/status/1", UrlCleaner.clean("https://x.com/user/status/1?s=20&t=tracker"))
        assertEquals("https://x.com/search?q=Kotlin%20Multiplatform&f=live", UrlCleaner.clean(
            "https://x.com/search?q=Kotlin%20Multiplatform&f=live&src=typed_query",
        ))
    }

    @Test
    fun removesTwitterReferralTracking() {
        for (host in listOf("x.com", "twitter.com", "mobile.twitter.com")) {
            val post = "https://$host/user/status/1"
            assertEquals("$post?lang=en", UrlCleaner.clean(
                "$post?s=20&t=tracker&%63xt=context&cn=campaign&refsrc=share&src=share&twclid=click&lang=en",
            ))
        }
        assertEquals("https://example.com/article?lang=en", UrlCleaner.clean(
            "https://example.com/article?twclid=click&lang=en",
        ))
    }

    @Test
    fun preservesTwitterRedirectQueries() {
        val redirect = "https://twitter.com/i/redirect?url=https%3A%2F%2Fexample.com&t=token&src=email"
        assertEquals(redirect, UrlCleaner.clean(redirect))
    }

    @Test
    fun removesSiteTrackingAndPreservesContentParameters() {
        val links = mapOf(
            "https://facebook.com/watch?v=123&comment_id=456&__cft__%5B0%5D=tracking&__tn__=tracking&sfnsn=share" to
                "https://facebook.com/watch?v=123&comment_id=456",
            "https://instagram.com/p/ABC123/?ig_rid=tracker&igsh=tracker&img_index=2" to
                "https://instagram.com/p/ABC123?img_index=2",
            "https://instagr.am/p/ABC123/?ig_rid=tracker&img_index=2" to
                "https://instagr.am/p/ABC123?img_index=2",
            "https://tiktok.com/@user/video/123?_t=tracker&_r=1&share_iid=123&lang=en" to
                "https://tiktok.com/@user/video/123?lang=en",
            "https://reddit.com/r/test/comments/abc/title/def?ref_source=share&ref_campaign=share&context=3" to
                "https://reddit.com/r/test/comments/abc/title/def?context=3",
            "https://open.spotify.com/episode/123?si=tracker&t=42" to
                "https://open.spotify.com/episode/123?t=42",
            "https://example.com/path?cxt=1&src=manual&si=2&_r=1" to
                "https://example.com/path?cxt=1&src=manual&si=2&_r=1",
        )
        for ((input, expected) in links) {
            assertEquals(expected, UrlCleaner.clean(input), input)
        }
    }

    @Test
    fun preservesCaseOutsideTheHostAndHandlesUserInfo() {
        assertEquals("https://example.com?Token=ABC/Def", UrlCleaner.clean(
            "https://EXAMPLE.com?Token=ABC/Def&utm_source=test",
        ))
        assertEquals("https://example.com#Section/Part", UrlCleaner.clean("https://EXAMPLE.com#Section/Part"))
        assertEquals("https://User:Pass@example.com/path", UrlCleaner.clean("https://User:Pass@EXAMPLE.com/path"))
        assertEquals("evil.example", UrlCleaner.hostOf("https://x.com:443@evil.example/path"))
        assertEquals("[::1]", UrlCleaner.hostOf("http://[::1]:9000/path"))
    }

    @Test
    fun recognizesEncodedTrackingKeysWithoutReencodingValues() {
        assertEquals("https://example.com/path?keep=A%2FB", UrlCleaner.clean(
            "https://example.com/path?%75tm_source=tracker&keep=A%2FB",
        ))
    }
}
