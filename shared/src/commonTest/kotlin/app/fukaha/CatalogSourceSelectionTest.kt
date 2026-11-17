package app.fukaha

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogSourceSelectionTest {
    @Test
    fun everyCatalogChoiceReachesThePreparedShareLink() = runTest {
        val catalog = EmbedCatalog.bundled()
        val bridge = FukahaBridge(LinkProcessor(catalog, HttpClient(MockEngine {
            error("Selecting a catalog source must not require a network request")
        })))
        try {
            for (platform in catalog.platformKeys()) {
                val original = "${catalog.platform(platform)!!.href}/catalog-validation/123?item=42&utm_source=test"
                for (service in catalog.activeServices(platform)) {
                    val host = service.normalizedHost()
                    val expected = "$host/catalog-validation/123?item=42"
                    for (preference in listOf(host, service.name) + service.alternateHosts) {
                        val settings = FukahaSettings(
                            preferredFixers = mapOf(platform to preference), resolveShortLinks = false,
                        )
                        val prepared = assertNotNull(bridge.prepare(original, settings, mapOf(host to EmbedHealthStatus.Dead)))
                        assertEquals(platform, prepared.detected.platformKey)
                        assertEquals(expected, prepared.textUrlFor(ShareAction.Embed), "$platform: $preference")
                    }
                    val allowed = assertNotNull(EmbedPreviewChecker.allowedHosts(original, expected))
                    assertTrue(EmbedPreviewChecker.sameTarget(expected, expected, allowed), host)
                }
            }
        } finally { bridge.close() }
    }
}
