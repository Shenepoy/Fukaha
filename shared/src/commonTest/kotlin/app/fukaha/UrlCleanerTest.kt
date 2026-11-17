package app.fukaha

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UrlCleanerTest {
    @Test
    fun extractsFirstUrl() {
        val text = "check this https://x.com/user/status/123?utm_source=share and more"
        assertEquals(
            "https://x.com/user/status/123?utm_source=share",
            UrlCleaner.extractFirstUrl(text),
        )
    }

    @Test
    fun extractsUrlFromSharedHtml() {
        val html = "<a href=\"https://x.com/user/status/123?utm_source=share\">open</a>"
        assertEquals(
            "https://x.com/user/status/123?utm_source=share",
            UrlCleaner.extractFirstUrl(html),
        )
    }

    @Test
    fun stripsTrackingParams() {
        val dirty = "https://www.instagram.com/reel/ABC123/?igshid=xyz&utm_source=ig&utm_medium=social&si=1"
        val clean = UrlCleaner.clean(dirty)
        assertEquals("https://instagram.com/reel/ABC123", clean)
        assertFalse(clean.contains("utm_"))
        assertFalse(clean.contains("igshid"))
        assertFalse(clean.contains("si="))
    }

    @Test
    fun detectsShortLinks() {
        assertTrue(UrlCleaner.isShortLink("https://t.co/abcdef"))
        assertTrue(UrlCleaner.isShortLink("https://vm.tiktok.com/ZMxxxxx/"))
        assertFalse(UrlCleaner.isShortLink("https://tiktok.com/@user/video/1"))
    }
}

class EmbedCatalogTest {
    private val catalog = EmbedCatalog.bundled()

    @Test
    fun detectsTwitterAsXAndRewrites() {
        val url = "https://twitter.com/foo/status/123456?utm_source=share"
        val detected = catalog.detect(url)
        assertEquals("x", detected.platformKey)
        assertEquals("https://twitter.com/foo/status/123456", detected.cleanedUrl)
        val embed = catalog.rewriteToEmbed(url)
        assertEquals("https://fixvx.com/foo/status/123456", embed)
    }

    @Test
    fun listsXOnceWithoutSeparateTwitterEntry() {
        val keys = catalog.platformKeys()
        assertTrue(keys.contains("x"))
        assertFalse(keys.contains("twitter"))
    }

    @Test
    fun detectsXAndUsesFixvx() {
        val embed = catalog.rewriteToEmbed("https://x.com/foo/status/99")
        assertEquals("https://fixvx.com/foo/status/99", embed)
    }

    @Test
    fun prefersCustomFixer() {
        val embed = catalog.rewriteToEmbed(
            "https://tiktok.com/@a/video/1",
            preferredFixerHost = "https://tiktxk.com",
        )
        assertEquals("https://tiktxk.com/@a/video/1", embed)
    }

    @Test
    fun omitsRetiredTwitchServicesAndUsesSeria() {
        val twitch = catalog.platform("twitch")
        assertNotNull(twitch)
        assertEquals(listOf("https://fxtwitch.seria.moe"), twitch.services.map { it.normalizedHost() })
        assertTrue(catalog.activeServices("twitch").any { it.normalizedHost() == "https://fxtwitch.seria.moe" })
        assertEquals("https://fxtwitch.seria.moe/somechannel", catalog.rewriteToEmbed("https://twitch.tv/somechannel"))
    }

    @Test
    fun listsActiveInstagramServices() {
        val services = catalog.activeServices("instagram")
        assertFalse(services.any { it.normalizedHost() == "https://ddinstagram.com" })
        assertTrue(services.any { it.normalizedHost() == "https://vxinstagram.com" })
        assertNotNull(catalog.defaultFixerHost("instagram"))
    }

    @Test
    fun retiredPreferencesFallBackToCurrentDefaults() {
        val replacements = listOf(
            Triple("https://instagram.com/reel/ABC123", "https://ddinstagram.com", "https://vxinstagram.com/reel/ABC123"),
            Triple("https://instagram.com/reel/ABC123", "InstaFix", "https://vxinstagram.com/reel/ABC123"),
            Triple("https://reddit.com/r/flags/comments/1hqed7c", "https://rxddit.com", "https://vxreddit.com/r/flags/comments/1hqed7c"),
            Triple("https://threads.com/@zuck/post/DTa3-B1EbTp", "https://fixthreads.net", "https://fixthreads.seria.moe/@zuck/post/DTa3-B1EbTp"),
            Triple("https://tiktok.com/@scout2015/video/6718335390845095173", "https://vxtiktok.com", "https://tnktok.com/@scout2015/video/6718335390845095173"),
        )
        for ((original, retired, replacement) in replacements) {
            assertEquals(replacement, catalog.rewriteToEmbed(original, preferredFixerHost = retired))
        }
        for ((platform, host) in EmbedCatalog.DEFAULT_FIXERS) {
            assertNotNull(catalog.serviceForHost(platform, host), "Missing default for $platform")
        }
    }

    @Test
    fun detectsNewPlatforms() {
        assertEquals("youtube", catalog.detect("https://youtu.be/dQw4w9wg").platformKey)
        assertEquals("https://koutube.com/watch?v=dQw4w9wg", catalog.rewriteToEmbed("https://youtube.com/watch?v=dQw4w9wg"))
        assertEquals("facebook", catalog.detect("https://facebook.com/share/v/abc").platformKey)
        assertEquals("https://facebed.com/share/v/abc", catalog.rewriteToEmbed("https://facebook.com/share/v/abc"))
        assertEquals("https://fxbsky.app/profile/a.bsky.social/post/1", catalog.rewriteToEmbed(
            "https://bsky.app/profile/a.bsky.social/post/1",
            preferredFixerHost = "https://fxbsky.app",
        ))
    }

    @Test
    fun listsFamousPlatformsFirstAndOmitsSketchyOnes() {
        val keys = catalog.platformKeys()
        assertEquals(listOf("youtube", "instagram", "tiktok", "facebook", "x"), keys.take(5))
        assertTrue("furaffinity" !in keys)
        assertTrue("ifunny" !in keys)
        assertTrue("newgrounds" !in keys)
    }

    @Test
    fun catalogHasNoDuplicateHosts() {
        val hosts = catalog.platformKeys().flatMap { key ->
            catalog.platform(key)?.services.orEmpty().flatMap { service ->
                listOf(service.normalizedHost().lowercase()) +
                    service.alternateHosts.map { it.trim().trimEnd('/').lowercase() }
            }
        }
        val duplicates = hosts.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue(duplicates.isEmpty(), "duplicate hosts: $duplicates")
    }

    @Test
    fun bundledCatalogIsSharedAndFromJsonIsIndependent() {
        assertTrue(EmbedCatalog.bundled() === EmbedCatalog.bundled())
        val custom = EmbedCatalog.fromJson(
            """{"x":{"href":"https://x.com","name":"X","services":[]}}""",
        )
        assertTrue(custom !== EmbedCatalog.bundled())
        assertEquals(listOf("x"), custom.platformKeys())
        assertTrue(custom.activeServices("x").isEmpty())
    }

    @Test
    fun derivedLookupsStayStableAcrossCalls() {
        assertTrue(catalog.platformKeys() === catalog.platformKeys())
        assertTrue(catalog.activeServices("instagram") === catalog.activeServices("instagram"))
        assertEquals(
            catalog.defaultFixerHost("instagram"),
            catalog.defaultFixerHost("instagram"),
        )
    }
}
