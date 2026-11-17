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
