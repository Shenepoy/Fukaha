package app.fukaha

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkPreferencesTest {
    @Test fun disabledSiteCleansForEmbedAndRetainsItsChosenSource() = runTest {
        val processor = LinkProcessor()
        try {
            val settings = FukahaSettings(defaultAction = ShareAction.Embed, resolveShortLinks = false,
                preferredFixers = mapOf("x" to "fixupx.com"), disabledEmbedPlatforms = setOf("x"))
            for (host in listOf("x.com", "twitter.com", "mobile.twitter.com")) {
                val link = assertNotNull(processor.prepare("https://$host/person/status/123?utm_source=share", settings))
                assertNull(link.embedUrl)
                assertEquals("https://$host/person/status/123", link.textUrlFor(settings.effectiveDefaultAction()))
            }
            val enabled = assertNotNull(processor.prepare("https://x.com/person/status/123", settings.copy(disabledEmbedPlatforms = emptySet())))
            assertEquals("https://fixupx.com/person/status/123", enabled.embedUrl)
            assertNotNull(processor.prepare("https://youtube.com/watch?v=dQw4w9WgXcQ", settings)?.embedUrl)
        } finally { processor.close() }
    }

    @Test fun shorteningPreservesContextAndDoesNotRewriteEmbedProviderPaths() = runTest {
        val processor = LinkProcessor()
        try {
            val input = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42&list=PLabc&index=3&si=tracker#chapter"
            val normal = assertNotNull(processor.prepare(input, FukahaSettings(resolveShortLinks = false)))
            val settings = FukahaSettings(resolveShortLinks = false, useOfficialShortLinks = true)
            val short = assertNotNull(processor.prepare(input, settings))
            assertEquals("https://youtu.be/dQw4w9WgXcQ?t=42&list=PLabc&index=3#chapter", short.detected.cleanedUrl)
            assertEquals(normal.embedUrl, short.embedUrl)
            val disabled = assertNotNull(processor.prepare(input, settings.copy(disabledEmbedPlatforms = setOf("youtube"))))
            assertEquals(short.detected.cleanedUrl, disabled.textUrlFor(ShareAction.Embed))
        } finally { processor.close() }
    }

    @Test fun shorteningRejectsAmbiguousAndUnsupportedUrls() {
        for (url in listOf(
            "https://youtube.com/watch?v=invalid",
            "https://youtube.com/watch?v=dQw4w9WgXcQ&v=abcdefghijk",
            "https://youtube.com/playlist?list=PLabc",
            "https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ",
            "https://youtube.com:444/watch?v=dQw4w9WgXcQ",
            "https://name:pass@youtube.com/watch?v=dQw4w9WgXcQ",
            "https://x.com/person/status/123", "https://youtu.be/dQw4w9WgXcQ?t=42",
        )) assertEquals(url, OfficialShortLinks.shorten(url))
        assertEquals("https://youtu.be/dQw4w9WgXcQ", OfficialShortLinks.shorten("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun staleSnapshotOnlyChangesTheSiteEditedByTheUser() {
        val before = FukahaSettings(disabledEmbedPlatforms = setOf("x"))
        val latest = before.copy(disabledEmbedPlatforms = setOf("x", "instagram"), useOfficialShortLinks = true)
        val merged = latest.applyChangesFrom(before, before.copy(disabledEmbedPlatforms = emptySet()))
        assertEquals(setOf("instagram"), merged.disabledEmbedPlatforms)
        assertEquals(true, merged.useOfficialShortLinks)
        assertFalse(FukahaSettings().useOfficialShortLinks)
    }
}
