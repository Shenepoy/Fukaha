package app.fukaha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
    fun platformWithoutEmbedSourcesFallsBackToCleanOfficialLink() = runTest {
        val emptyCatalog = EmbedCatalog.fromJson(
            """{"roblox":{"href":"https://roblox.com","name":"Roblox","services":[]}}""",
        )
        val processor = LinkProcessor(emptyCatalog)
        try {
            val prepared = processor.prepare(
                "https://www.roblox.com/games/920587237/Adopt-Me?utm_source=share",
                FukahaSettings(preferredFixers = mapOf("roblox" to "https://fixroblox.com")),
            )
            assertNotNull(prepared)
            assertEquals("roblox", prepared.detected.platformKey)
            assertNull(prepared.embedUrl)
            assertEquals("https://roblox.com/games/920587237/Adopt-Me", prepared.textUrlFor(ShareAction.Embed))
        } finally {
            processor.close()
        }
    }

    @Test
    fun retiredRobloxPreferenceUsesNewProvider() = runTest {
        val processor = LinkProcessor(catalog)
        try {
            val prepared = processor.prepare(
                "https://www.roblox.com/games/920587237/Adopt-Me?utm_source=share",
                FukahaSettings(preferredFixers = mapOf("roblox" to "https://fixroblox.com")),
            )
            assertNotNull(prepared)
            assertEquals("https://www.fxroblox.com/games/920587237/Adopt-Me", prepared.embedUrl)
            assertEquals(prepared.embedUrl, prepared.textUrlFor(ShareAction.Embed))
        } finally {
            processor.close()
        }
    }

    @Test
    fun prepareCleansAndRewritesWithoutHittingTheNetwork() = runTest {
        val processor = LinkProcessor(catalog)
        try {
            val prepared = processor.prepare(
                sharedText = "watch https://twitter.com/foo/status/99?utm_source=share&cxt=timeline&cn=share&lang=en please",
                settings = FukahaSettings(resolveShortLinks = true),
            )
            assertNotNull(prepared)
            assertEquals("x", prepared.detected.platformKey)
            assertEquals("https://twitter.com/foo/status/99?lang=en", prepared.textUrlFor(ShareAction.Clean))
            assertEquals("https://fixvx.com/foo/status/99?lang=en", prepared.textUrlFor(ShareAction.Embed))
            assertEquals(EmbedHealthStatus.Unknown, prepared.embedHealth)
        } finally {
            processor.close()
        }
    }

    @Test
    fun prepareCleansTheDestinationOfAnXShortLink() = runTest {
        val shortLink = "https://t.co/example"
        val destination = "https://x.com/foo/status/99?cxt=timeline&t=tracker&twclid=click&lang=en"
        val requests = mutableListOf<String>()
        val processor = LinkProcessor(catalog, HttpClient(MockEngine { request ->
            val url = request.url.toString()
            requests.add(url)
            if (url == shortLink) {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, destination))
            } else {
                respond("")
            }
        }))
        try {
            val prepared = assertNotNull(processor.prepare(shortLink, FukahaSettings()))
            assertEquals(listOf(shortLink, destination), requests)
            assertEquals("https://x.com/foo/status/99?lang=en", prepared.textUrlFor(ShareAction.Clean))
            assertEquals("https://fixvx.com/foo/status/99?lang=en", prepared.textUrlFor(ShareAction.Embed))
        } finally {
            processor.close()
        }
    }

    @Test
    fun prepareKeepsAnXShortLinkWhenItCannotBeExpanded() = runTest {
        val shortLink = "https://t.co/example"
        var requests = 0
        val processor = LinkProcessor(catalog, HttpClient(MockEngine {
            requests++
            error("Simulated connection failure")
        }))
        try {
            val disabled = assertNotNull(processor.prepare(shortLink, FukahaSettings(resolveShortLinks = false)))
            assertEquals(0, requests)
            val unavailable = assertNotNull(processor.prepare(shortLink, FukahaSettings()))
            assertEquals(1, requests)
            for (prepared in listOf(disabled, unavailable)) {
                assertNull(prepared.embedUrl)
                assertEquals(shortLink, prepared.textUrlFor(ShareAction.Clean))
                assertEquals(shortLink, prepared.textUrlFor(ShareAction.Embed))
            }
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
            assertEquals("https://vxinstagram.com/reel/ABC123", prepared.embedUrl)
        } finally {
            bridge.close()
        }
    }
}
