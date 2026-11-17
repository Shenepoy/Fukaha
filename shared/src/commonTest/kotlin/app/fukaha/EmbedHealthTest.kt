package app.fukaha

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class EmbedHealthTest {
    private val catalog = EmbedCatalog.bundled()

    private fun service(host: String): EmbedService =
        EmbedService(name = host, host = host)

    @Test
    fun preferredAliveOrUnknownIsUsed() {
        val preferred = service("https://preferred.example")
        val other = service("https://other.example")
        val health = mapOf(
            EmbedHealthKeys.normalize(preferred.normalizedHost()) to EmbedHealthStatus.Unknown,
            EmbedHealthKeys.normalize(other.normalizedHost()) to EmbedHealthStatus.Alive,
        )
        val picked = EmbedHealthPolicy.pickService(
            services = listOf(preferred, other),
            preferred = preferred,
            default = other,
            health = health,
        )
        assertEquals(preferred.normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun explicitPickIsKeptEvenWhenAnotherHostIsAlive() {
        val preferred = service("https://dead.example")
        val alive = service("https://alive.example")
        val health = mapOf(
            EmbedHealthKeys.normalize(preferred.normalizedHost()) to EmbedHealthStatus.Dead,
            EmbedHealthKeys.normalize(alive.normalizedHost()) to EmbedHealthStatus.Alive,
        )
        val picked = EmbedHealthPolicy.pickService(
            services = listOf(preferred, alive),
            preferred = preferred,
            default = preferred,
            health = health,
        )
        assertEquals(preferred.normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun automaticSelectionPrefersUnprobedOverDead() {
        val preferred = service("https://dead.example")
        val unprobed = service("https://unprobed.example")
        val health = mapOf(
            EmbedHealthKeys.normalize(preferred.normalizedHost()) to EmbedHealthStatus.Dead,
        )
        val picked = EmbedHealthPolicy.pickService(
            services = listOf(preferred, unprobed),
            preferred = null,
            default = preferred,
            health = health,
        )
        assertEquals(unprobed.normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun explicitPickDoesNotMoveToNextAliveService() {
        val services = listOf(
            service("https://alive-before.example"),
            service("https://dead.example"),
            service("https://alive-after.example"),
        )
        val health = mapOf(
            EmbedHealthKeys.normalize(services[0].normalizedHost()) to EmbedHealthStatus.Alive,
            EmbedHealthKeys.normalize(services[1].normalizedHost()) to EmbedHealthStatus.Dead,
            EmbedHealthKeys.normalize(services[2].normalizedHost()) to EmbedHealthStatus.Alive,
        )
        val picked = EmbedHealthPolicy.pickService(
            services = services,
            preferred = services[1],
            default = services[0],
            health = health,
        )
        assertEquals(services[1].normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun explicitPickDoesNotWrapToFront() {
        val services = listOf(
            service("https://alive-first.example"),
            service("https://dead-middle.example"),
            service("https://dead-last.example"),
        )
        val health = mapOf(
            EmbedHealthKeys.normalize(services[0].normalizedHost()) to EmbedHealthStatus.Alive,
            EmbedHealthKeys.normalize(services[1].normalizedHost()) to EmbedHealthStatus.Dead,
            EmbedHealthKeys.normalize(services[2].normalizedHost()) to EmbedHealthStatus.Dead,
        )
        val picked = EmbedHealthPolicy.pickService(
            services = services,
            preferred = services[2],
            default = services[1],
            health = health,
        )
        assertEquals(services[2].normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun automaticSelectionPrefersAliveOverUnknown() {
        val services = listOf(
            service("https://alive-first.example"),
            service("https://dead.example"),
            service("https://unprobed.example"),
        )
        val health = mapOf(
            EmbedHealthKeys.normalize(services[0].normalizedHost()) to EmbedHealthStatus.Alive,
            EmbedHealthKeys.normalize(services[1].normalizedHost()) to EmbedHealthStatus.Dead,
        )
        val picked = EmbedHealthPolicy.pickService(
            services = services,
            preferred = null,
            default = services[2],
            health = health,
        )
        assertEquals(services[0].normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun withoutAPickTheCatalogueDefaultStillLeads() {
        val services = listOf(
            service("https://alive-first.example"),
            service("https://default.example"),
        )
        val health = mapOf(
            EmbedHealthKeys.normalize(services[0].normalizedHost()) to EmbedHealthStatus.Alive,
            EmbedHealthKeys.normalize(services[1].normalizedHost()) to EmbedHealthStatus.Alive,
        )
        val picked = EmbedHealthPolicy.pickService(
            services = services,
            preferred = null,
            default = services[1],
            health = health,
        )
        assertEquals(services[1].normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun stalePickOutsideTheListFallsBackToTheDefault() {
        val services = listOf(
            service("https://alive-first.example"),
            service("https://default.example"),
        )
        val retired = service("https://retired.example")
        val health = mapOf(
            EmbedHealthKeys.normalize(retired.normalizedHost()) to EmbedHealthStatus.Dead,
            EmbedHealthKeys.normalize(services[0].normalizedHost()) to EmbedHealthStatus.Alive,
            EmbedHealthKeys.normalize(services[1].normalizedHost()) to EmbedHealthStatus.Alive,
        )
        val picked = EmbedHealthPolicy.pickService(
            services = services,
            preferred = retired,
            default = services[1],
            health = health,
        )
        assertEquals(services[1].normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun everyHostDeadKeepsTheUsersOwnPick() {
        val services = listOf(
            service("https://dead-a.example"),
            service("https://dead-b.example"),
            service("https://dead-c.example"),
        )
        val health = services.associate {
            EmbedHealthKeys.normalize(it.normalizedHost()) to EmbedHealthStatus.Dead
        }
        val picked = EmbedHealthPolicy.pickService(
            services = services,
            preferred = services[1],
            default = services[0],
            health = health,
        )
        assertEquals(services[1].normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun effectiveServiceReportsTheExplicitPickDespiteFailedProbe() {
        val services = catalog.activeServices("threads")
        assertTrue(services.size >= 2)
        val dead = services.first()
        val alive = services[1]
        val health = mapOf(
            EmbedHealthKeys.normalize(dead.normalizedHost()) to EmbedHealthStatus.Dead,
            EmbedHealthKeys.normalize(alive.normalizedHost()) to EmbedHealthStatus.Alive,
        )
        val effective = catalog.effectiveService("threads", dead.normalizedHost(), health)
        assertEquals(dead.normalizedHost(), effective?.normalizedHost())
        assertEquals(
            effective?.normalizedHost(),
            catalog.rewriteToEmbed(
                url = "https://threads.net/@a/post/1",
                preferredFixerHost = dead.normalizedHost(),
                health = health,
            )?.let { UrlCleaner.hostOf(it) }?.let { "https://$it" },
        )
    }

    @Test
    fun serviceForHostMatchesAlternateHosts() {
        val fx = catalog.serviceForHost("x", "https://fxtwitter.com")
        assertEquals("FxEmbed", fx?.name)
        assertEquals("https://fixupx.com", fx?.normalizedHost())
    }

    @Test
    fun allDeadFallsBackToPreferred() {
        val preferred = service("https://dead-a.example")
        val other = service("https://dead-b.example")
        val health = mapOf(
            EmbedHealthKeys.normalize(preferred.normalizedHost()) to EmbedHealthStatus.Dead,
            EmbedHealthKeys.normalize(other.normalizedHost()) to EmbedHealthStatus.Dead,
        )
        val picked = EmbedHealthPolicy.pickService(
            services = listOf(preferred, other),
            preferred = preferred,
            default = other,
            health = health,
        )
        assertEquals(preferred.normalizedHost(), picked?.normalizedHost())
    }

    @Test
    fun rewriteKeepsExplicitTwitterFixer() {
        val health = mapOf(
            EmbedHealthKeys.normalize("https://fixvx.com") to EmbedHealthStatus.Dead,
            EmbedHealthKeys.normalize("https://fixupx.com") to EmbedHealthStatus.Dead,
            EmbedHealthKeys.normalize("https://twitterez.com") to EmbedHealthStatus.Alive,
        )
        val services = catalog.activeServices("x")
        assertTrue(services.size >= 2)
        val embed = catalog.rewriteToEmbed(
            url = "https://twitter.com/foo/status/99",
            preferredFixerHost = "https://fixvx.com",
            health = health,
        )
        assertEquals("https://fixvx.com/foo/status/99", embed)
    }

    @Test
    fun runWithNoAliveHostIsNotUsable() {
        val allDead = mapOf(
            "https://a.example" to EmbedHealthStatus.Dead,
            "https://b.example" to EmbedHealthStatus.Dead,
        )
        assertFalse(EmbedHealthPolicy.isUsableResult(allDead))
        assertFalse(EmbedHealthPolicy.isUsableResult(emptyMap()))
        assertTrue(
            EmbedHealthPolicy.isUsableResult(
                allDead + ("https://c.example" to EmbedHealthStatus.Alive),
            ),
        )
    }

    @Test
    fun manualCheckCoolsDownAfterASavedRun() {
        val checkedAt = 10_000L
        assertEquals(
            EmbedHealthPolicy.MANUAL_REFRESH_COOLDOWN_MS,
            EmbedHealthPolicy.cooldownRemainingMs(checkedAt, nowEpochMs = checkedAt),
        )
        assertEquals(
            0L,
            EmbedHealthPolicy.cooldownRemainingMs(
                checkedAt,
                nowEpochMs = checkedAt + EmbedHealthPolicy.MANUAL_REFRESH_COOLDOWN_MS,
            ),
        )
        assertEquals(0L, EmbedHealthPolicy.cooldownRemainingMs(null, nowEpochMs = checkedAt))
    }

    @Test
    fun classifyProbeResult() {
        assertEquals(EmbedHealthStatus.Alive, EmbedHealthChecker.classifyProbeResult(true))
        assertEquals(EmbedHealthStatus.Dead, EmbedHealthChecker.classifyProbeResult(false))
    }

    @Test
    fun missingHomepageIsUnverified() = runTest {
        val checker = EmbedHealthChecker.createForTests(testCatalog("https://dead.example")) { _, _ -> 404 }

        try {
            assertEquals(EmbedHealthStatus.Unknown, checker.probe("https://dead.example"))
        } finally {
            checker.close()
        }
    }

    @Test
    fun headMethodNotAllowedFallsBackToGet() = runTest {
        val methods = mutableListOf<EmbedHealthRequestMethod>()
        val checker = EmbedHealthChecker.createForTests(testCatalog("https://head-only.example")) { _, method ->
            methods += method
            if (method == EmbedHealthRequestMethod.Head) 405 else 200
        }

        try {
            assertEquals(EmbedHealthStatus.Alive, checker.probe("https://head-only.example"))
            assertEquals(
                listOf(EmbedHealthRequestMethod.Head, EmbedHealthRequestMethod.Get),
                methods,
            )
        } finally {
            checker.close()
        }
    }

    @Test
    fun refreshUsesConcurrentWorkers() = runTest {
        var activeRequests = 0
        var maximumActiveRequests = 0
        val checker = EmbedHealthChecker.createForTests(
            testCatalog(
                "https://one.example",
                "https://two.example",
                "https://three.example",
            ),
        ) { _, _ ->
            activeRequests += 1
            maximumActiveRequests = maxOf(maximumActiveRequests, activeRequests)
            delay(10)
            activeRequests -= 1
            200
        }

        try {
            assertEquals(3, checker.refresh().size)
            assertTrue(maximumActiveRequests > 1)
        } finally {
            checker.close()
        }
    }

    @Test
    fun uniqueHostsAreCachedDistinctAndSorted() {
        val checker = EmbedHealthChecker.create(catalog)
        try {
            val hosts = checker.uniqueHosts()
            assertTrue(hosts.isNotEmpty())
            assertEquals(hosts, hosts.distinct())
            assertEquals(hosts.sorted(), hosts)
            assertTrue(hosts === checker.uniqueHosts())
            assertTrue(hosts.all { it.startsWith("https://") && !it.endsWith("/") })
        } finally {
            checker.close()
        }
    }

    @Test
    fun autoRefreshDueWhenNeverChecked() {
        assertTrue(EmbedHealthPolicy.isDue(null, nowEpochMs = 1_000L))
        assertFalse(
            EmbedHealthPolicy.isDue(
                checkedAtEpochMs = 1_000L,
                nowEpochMs = 1_000L + EmbedHealthPolicy.AUTO_REFRESH_INTERVAL_MS - 1,
            ),
        )
        assertTrue(
            EmbedHealthPolicy.isDue(
                checkedAtEpochMs = 1_000L,
                nowEpochMs = 1_000L + EmbedHealthPolicy.AUTO_REFRESH_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun normalizeHealthKeys() {
        assertEquals("https://fixvx.com", EmbedHealthKeys.normalize("https://fixvx.com/"))
        assertEquals("https://fixvx.com", EmbedHealthKeys.normalize("fixvx.com"))
        assertEquals("https://fixvx.com", EmbedHealthKeys.normalize("HTTPS://FixVX.com"))
    }

    @Test
    fun progressShowsHostAndFraction() {
        val progress = EmbedHealthProgress(
            currentHost = "https://koutube.com/",
            currentIndex = 12,
            total = 40,
            aliveCount = 8,
            deadCount = 3,
        )
        assertEquals("koutube.com", progress.displayHost)
        assertEquals(12, progress.completedCount)
        assertEquals(12f / 40f, progress.fraction)
        assertEquals(2, EmbedHealthSnapshot(
            statuses = mapOf(
                "https://a.example" to EmbedHealthStatus.Alive,
                "https://b.example" to EmbedHealthStatus.Alive,
                "https://c.example" to EmbedHealthStatus.Dead,
            ),
        ).aliveCount)
    }

    private fun testCatalog(vararg hosts: String): EmbedCatalog =
        EmbedCatalog(
            mapOf(
                "test" to EmbedPlatform(
                    services = hosts.map { host -> service(host) },
                ),
            ),
        )
}
