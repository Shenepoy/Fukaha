package app.fukaha

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class LinkProcessorTest {
    private val catalog = EmbedCatalog.bundled()

    @Test
    fun preparedLinkSelectsCleanAndEmbedUrlsWithSafeFallback() {
        val detected = DetectedLink(
            originalUrl = "https://x.com/user/status/1?utm_source=test",
            cleanedUrl = "https://x.com/user/status/1",
            platformKey = "x",
            platformName = "X",
        )
        val withEmbed = PreparedLink(detected, "https://fixvx.com/user/status/1")
        val withoutEmbed = PreparedLink(detected, null)

        assertEquals(detected.cleanedUrl, withEmbed.textUrlFor(ShareAction.Clean))
        assertEquals("https://fixvx.com/user/status/1", withEmbed.textUrlFor(ShareAction.Embed))
        assertEquals(detected.cleanedUrl, withoutEmbed.textUrlFor(ShareAction.Embed))
        assertNull(withEmbed.textUrlFor(ShareAction.Ask))
        assertNull(withEmbed.textUrlFor(ShareAction.Download))
    }

    @Test
    fun prepareReturnsNullWhenTextHasNoUrl() = runTest {
        val processor = LinkProcessor(catalog)
        try {
            assertNull(processor.prepare("hello there", FukahaSettings()))
        } finally {
            processor.close()
        }
    }

    @Test
    fun prepareCleansAndRewritesWithoutHittingTheNetwork() = runTest {
        val processor = LinkProcessor(catalog)
        try {
            val prepared = processor.prepare(
                sharedText = "watch https://twitter.com/foo/status/99?utm_source=share please",
                settings = FukahaSettings(resolveShortLinks = true),
            )
            assertNotNull(prepared)
            assertEquals("x", prepared.detected.platformKey)
            assertEquals("https://twitter.com/foo/status/99", prepared.detected.cleanedUrl)
            assertEquals("https://fixvx.com/foo/status/99", prepared.embedUrl)
            assertEquals(EmbedHealthStatus.Unknown, prepared.embedHealth)
        } finally {
            processor.close()
        }
    }

    @Test
    fun prepareHonorsPreferredFixerAndHealth() = runTest {
        val processor = LinkProcessor(catalog)
        try {
            val health = mapOf(
                EmbedHealthKeys.normalize("https://fixvx.com") to EmbedHealthStatus.Dead,
                EmbedHealthKeys.normalize("https://twitterez.com") to EmbedHealthStatus.Alive,
            )
            val prepared = processor.prepare(
                sharedText = "https://x.com/foo/status/99",
                settings = FukahaSettings(
                    preferredFixers = mapOf("x" to "https://fixvx.com"),
                    resolveShortLinks = false,
                ),
                health = health,
            )
            assertNotNull(prepared)
            assertEquals("https://fixvx.com/foo/status/99", prepared.embedUrl)
            assertEquals(EmbedHealthStatus.Dead, prepared.embedHealth)
        } finally {
            processor.close()
        }
    }

    @Test
    fun bridgePrepareMatchesProcessor() = runTest {
        val bridge = FukahaBridge(LinkProcessor(catalog))
        try {
            val prepared = bridge.prepare(
                text = "https://instagram.com/reel/ABC123/?igshid=xyz",
                settings = FukahaSettings(),
            )
            assertNotNull(prepared)
            assertEquals("instagram", prepared.detected.platformKey)
            assertEquals("https://instagram.com/reel/ABC123", prepared.detected.cleanedUrl)
            assertEquals("https://ddinstagram.com/reel/ABC123", prepared.embedUrl)
        } finally {
            bridge.close()
        }
    }
}
