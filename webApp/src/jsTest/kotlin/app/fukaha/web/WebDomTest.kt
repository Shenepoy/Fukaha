package app.fukaha.web

import app.fukaha.EmbedHealthProgress
import app.fukaha.EmbedHealthStatus
import app.fukaha.EmbedPreviewOutcome
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.events.Event
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WebDomTest {
    private lateinit var root: HTMLElement

    @BeforeTest
    fun setUp() {
        localStorage.removeItem("fukaha_settings")
        localStorage.removeItem("fukaha_source_preferences")
        localStorage.removeItem("fukaha_embed_health")
        root = document.createElement("div") as HTMLElement
        root.id = "test-app"
        document.body!!.appendChild(root)
    }

    @AfterTest
    fun tearDown() {
        root.remove()
        localStorage.removeItem("fukaha_settings")
        localStorage.removeItem("fukaha_source_preferences")
        localStorage.removeItem("fukaha_embed_health")
        document.documentElement?.setAttribute("lang", "en")
        document.documentElement?.setAttribute("dir", "ltr")
        document.documentElement?.removeAttribute("data-theme")
    }

    @Test
    fun siteEmbeddingAndOfficialShorteningControlsUpdatePreparedLinks() = MainScope().promise {
        val app = App(root)
        app.start(null)
        delay(30)
        app.prepare("https://youtube.com/watch?v=dQw4w9WgXcQ&t=42&si=tracking")
        delay(50)
        assertNotNull(app.prepared?.embedUrl)
        app.show(View.Settings)
        (root.querySelector("#embed-enabled-youtube") as HTMLElement).click()
        delay(50)
        assertNull(app.prepared?.embedUrl)
        assertTrue("youtube" in WebSettingsStore().get().disabledEmbedPlatforms)
        (root.querySelector("#official-short-links") as HTMLElement).click()
        delay(50)
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=42", app.prepared?.detected?.cleanedUrl)
        assertTrue(WebSettingsStore().get().useOfficialShortLinks)
        (root.querySelector("#embed-enabled-youtube") as HTMLElement).click()
        delay(50)
        assertNotNull(app.prepared?.embedUrl)
        assertTrue(WebSettingsStore().get().disabledEmbedPlatforms.isEmpty())
    }

    @Test
    fun previewActionRejectsStaleResultsWhenLinkChanges() = MainScope().promise {
        val originalFetch = window.asDynamic().fetch
        val app = App(root)
        try {
            app.start(null)
            delay(30)
            app.prepare("https://x.com/example/status/123")
            delay(30)
            var finish: ((dynamic) -> Unit)? = null
            var requestedUrl = ""
            var signal: dynamic = null
            window.asDynamic().fetch = { url: dynamic, options: dynamic ->
                requestedUrl = url as String
                signal = options.signal
                Promise<dynamic> { resolve, _ -> finish = resolve }
            }
            (root.querySelector("#preview-check") as HTMLButtonElement).click()
            delay(30)
            assertTrue(app.previewChecking)
            assertTrue((root.querySelector("#preview-check") as HTMLButtonElement).disabled)
            assertEquals(app.prepared!!.embedUrl, requestedUrl)

            app.prepare("https://x.com/example/status/999")
            delay(30)
            assertTrue(signal.aborted as Boolean)
            assertFalse(app.previewChecking)
            assertNull(app.previewResult)
            // A late response for the previous post cannot overwrite the new link's state.
            finish!!(js("({type:'opaque',status:0})"))
            delay(30)
            assertNull(app.previewResult)

            window.asDynamic().fetch = { _: dynamic, _: dynamic ->
                Promise.resolve<dynamic>(js("({type:'opaque',status:0})"))
            }
            (root.querySelector("#preview-check") as HTMLElement).click()
            delay(30)
            assertEquals(EmbedPreviewOutcome.NetworkError, app.previewResult?.outcome)
            assertTrue(root.textContent.orEmpty().contains(app.strings.previewNetworkError))
            assertFalse((root.querySelector("#preview-check") as HTMLButtonElement).disabled)
            app.show(View.Home)
            delay(220)
            assertNull(app.previewResult)
        } finally {
            window.asDynamic().fetch = originalFetch
        }
    }

    @Test
    fun failedStorageWriteLeavesSelectionAndPickerIntact() = MainScope().promise {
        val app = App(root)
        app.start(null)
        delay(30)
        app.show(View.Settings)
        app.toggleFixerPicker("x")
        val original = app.effectiveFixer("x")
        val storagePrototype: dynamic = js("Storage.prototype")
        val originalSet = storagePrototype.setItem
        try {
            storagePrototype.setItem = { _: dynamic, _: dynamic -> throw IllegalStateException("Storage denied") }
            (root.querySelectorAll(".fixer-option").item(1) as HTMLElement).click()
            delay(30)
            assertEquals(original, app.effectiveFixer("x"))
            assertEquals("x", app.openFixerPlatform)
            assertEquals(app.strings.storageUnavailable, app.status)
            val star = root.querySelector("[data-source-favorite]") as HTMLElement
            star.click()
            assertEquals("false", star.getAttribute("aria-pressed"))
            assertTrue(app.sourcePreferences.favorites.isEmpty())
        } finally {
            storagePrototype.setItem = originalSet
        }
    }

    @Test
    fun favoritesAndReorderingAreIndependentOfSelectionAndPersist() = MainScope().promise {
        val app = App(root)
        app.start(null)
        delay(30)
        app.show(View.Settings)
        app.toggleFixerPicker("x")
        val original = app.effectiveFixer("x")
        val services = app.servicesFor("x")
        val a = services[1].normalizedHost()
        val b = services[2].normalizedHost()
        (root.querySelector("[data-source-favorite='$a']") as HTMLElement).click()
        (root.querySelector("[data-source-favorite='$b']") as HTMLElement).click()
        assertEquals(original, app.effectiveFixer("x"))
        assertEquals("x", app.openFixerPlatform)
        (root.querySelector("#source-order-toggle") as HTMLElement).click()
        val row = root.querySelector("[data-source-host='$b']")!!
        (row.querySelector(".source-move") as HTMLElement).click()
        assertEquals(b, root.querySelector("[data-source-host]")?.getAttribute("data-source-host"))
        assertEquals(original, app.effectiveFixer("x"))
        assertEquals("true", root.querySelector("[data-source-favorite='$b']")?.getAttribute("aria-pressed"))
        assertEquals(root.querySelector("[data-source-favorite='$b']"), document.activeElement)
        // A fresh app instance models reloading the document with the same localStorage.
        val reloaded = App(root)
        reloaded.start(null)
        delay(30)
        reloaded.show(View.Settings)
        reloaded.toggleFixerPicker("x")
        assertEquals(b, root.querySelector("[data-source-host]")?.getAttribute("data-source-host"))
        assertEquals(original, reloaded.effectiveFixer("x"))
        (root.querySelector("[data-source-host='$b'] .fixer-option") as HTMLElement).click()
        delay(220)
        assertEquals(b, reloaded.effectiveFixer("x")?.normalizedHost())
        assertEquals(b, WebSettingsStore().get().preferredFixers["x"])
    }

    @Test
    fun unsuccessfulCheckIsVisibleAfterReload() = MainScope().promise {
        WebHealthStore().save(emptyMap(), 1234)
        val app = App(root)
        app.start(null)
        delay(30)
        app.show(View.Settings)
        val summary = root.querySelector("#$EMBED_HEALTH_SUMMARY_ID")!!.textContent.orEmpty()
        assertTrue(summary.contains(app.strings.embedHealthLastAttempt))
        assertTrue(summary.contains(app.strings.embedHealthOffline))
        assertFalse(summary.contains(app.strings.embedHealthNever))
    }

    @Test
    fun homeAndSettingsKeepStableTopBarSlotsAndDirectionalBackArrow() {
        val app = App(root)
        app.render()

        assertNotNull(root.querySelector(".top-app-bar-title-home"))
        assertNotNull(root.querySelector(".top-app-bar-route-action"))
        assertNotNull(root.querySelector(".top-app-bar-language"))
        assertNotNull(root.querySelector(".top-app-bar-theme"))
        assertNull(root.querySelector(".top-app-bar-navigation"))
        assertNotNull(root.querySelector("main.content .paste-section"))

        app.show(View.Settings)

        val back = root.querySelector(".top-app-bar-navigation") as HTMLElement
        assertTrue(back.classList.contains("icon-flip"))
        assertEquals(Icon.BACK, back.querySelector(".icon")?.textContent)
        assertNotNull(root.querySelector(".top-app-bar-placeholder[aria-hidden=true]"))
        assertNotNull(root.querySelector(".top-app-bar-language"))
        assertNotNull(root.querySelector(".top-app-bar-theme"))
        assertNotNull(root.querySelector("main.content .settings-first-section"))
        assertNull(root.querySelector(".paste-section"))
    }

    @Test
    fun healthProgressUpdatesOnlyItsSettingsBoundary() {
        val app = App(root)
        app.show(View.Settings)
        val unrelatedAction = root.querySelector(".settings-first-section .action-option")
        val summary = root.querySelector("#$EMBED_HEALTH_SUMMARY_ID")
        val progress = root.querySelector("#$EMBED_HEALTH_PROGRESS_ID") as HTMLElement
        assertNotNull(unrelatedAction)
        assertNotNull(summary)
        assertTrue(progress.hasAttribute("hidden"))

        app.updateHealthProgress(
            progress = EmbedHealthProgress(
                currentHost = "fixupx.com",
                currentIndex = 1,
                total = 3,
                aliveCount = 1,
            ),
            completedStatus = EmbedHealthStatus.Alive,
        )

        assertSame(unrelatedAction, root.querySelector(".settings-first-section .action-option"))
        assertSame(summary, root.querySelector("#$EMBED_HEALTH_SUMMARY_ID"))
        assertEquals(app.strings.embedHealthProgress(1, 3), summary.textContent)
        assertFalse(progress.hasAttribute("hidden"))
        assertEquals("1", progress.getAttribute("aria-valuenow"))
        assertTrue(
            (progress.querySelector(".progress-bar") as HTMLElement).style.transform.contains("0.333"),
        )
    }

    @Test
    fun aboutDisplaysCanonicalVersionAsAccessibleNonInteractiveText() {
        val app = App(root)
        app.show(View.Settings)

        val row = root.querySelector(".version-row") as HTMLElement
        val value = row.querySelector("[data-app-version]") as HTMLElement

        assertEquals("${app.strings.version}: $WEB_DISPLAY_VERSION", row.getAttribute("aria-label"))
        assertEquals(WEB_DISPLAY_VERSION, value.textContent)
        assertEquals(WEB_DISPLAY_VERSION, value.getAttribute("data-app-version"))
        assertEquals("ltr", value.dir)
        assertNull(row.getAttribute("href"))
        assertNull(row.getAttribute("role"))
    }

    @Test
    fun shareRouteRendersResponsiveModalStateAndAccessibleDialog() {
        val app = App(root)
        app.show(View.Share)

        val scrim = root.querySelector(".sheet-scrim")
        val dialog = root.querySelector(".shell.shell-share") as HTMLElement
        assertNotNull(scrim)
        assertEquals("dialog", dialog.getAttribute("role"))
        assertEquals("true", dialog.getAttribute("aria-modal"))
        assertEquals("page-title", dialog.getAttribute("aria-labelledby"))
        assertNotNull(dialog.querySelector(".sheet-handle[aria-hidden=true]"))
        assertNotNull(dialog.querySelector("main.content-share"))
        assertNull(dialog.querySelector(".top-app-bar"))
        assertEquals(1, root.querySelectorAll("#page-title").length)
        assertNotNull(root.querySelector(".shell:not(.shell-share) .paste-section"))
    }

    @Test
    fun closingShareBeforeSettingsReturnsToHome(): dynamic {
        val browserWindow = kotlinx.browser.window.asDynamic()
        val originalMatchMedia = browserWindow.matchMedia
        browserWindow.matchMedia =
            js("function(query) { return {matches: query === '(prefers-reduced-motion: reduce)'}; }")
        return MainScope().promise {
            try {
                val app = App(root)
                app.render()
                app.prepare(SAMPLE_LINK)
                delay(50)

                assertEquals(View.Share, app.view)
                assertNotNull(app.prepared)

                app.show(View.Home)
                assertNull(app.prepared)
                app.show(View.Settings)
                app.leaveSettings()

                assertEquals(View.Home, app.view)
                assertNotNull(root.querySelector("main.content .paste-section"))
                assertNull(root.querySelector(".sheet-scrim"))
            } finally {
                browserWindow.matchMedia = originalMatchMedia
            }
        }
    }

    @Test
    fun settingsOpenedFromShareStillReturnsToShare(): dynamic {
        val browserWindow = kotlinx.browser.window.asDynamic()
        val originalMatchMedia = browserWindow.matchMedia
        browserWindow.matchMedia =
            js("function(query) { return {matches: query === '(prefers-reduced-motion: reduce)'}; }")
        return MainScope().promise {
            try {
                val app = App(root)
                app.render()
                app.prepare(SAMPLE_LINK)
                delay(50)

                app.show(View.Settings)
                app.leaveSettings()

                assertEquals(View.Share, app.view)
                assertNotNull(app.prepared)
                assertNotNull(root.querySelector(".sheet-scrim"))
            } finally {
                browserWindow.matchMedia = originalMatchMedia
            }
        }
    }

    @Test
    fun languageMenuExposesSelectionStateAndDismissesOutside() {
        val app = App(root)
        app.render()
        val trigger = root.querySelector("#$LANGUAGE_MENU_TRIGGER_ID") as HTMLButtonElement

        trigger.click()
        val openTrigger = root.querySelector("#$LANGUAGE_MENU_TRIGGER_ID") as HTMLButtonElement

        assertEquals("true", openTrigger.getAttribute("aria-expanded"))
        assertEquals("menu", root.querySelector("#$LANGUAGE_MENU_ID")?.getAttribute("role"))
        val options = root.querySelectorAll("[role=menuitemradio]")
        assertEquals(LANGUAGE_OPTIONS.size, options.length)
        assertEquals(
            1,
            (0 until options.length).count {
                (options.item(it) as? Element)?.getAttribute("aria-checked") == "true"
            },
        )

        document.body!!.dispatchEvent(js("new Event('pointerdown', { bubbles: true })") as Event)

        assertEquals("false", openTrigger.getAttribute("aria-expanded"))
        assertTrue((root.querySelector("#$LANGUAGE_MENU_ID") as HTMLElement).classList.contains("motion-exit"))
    }

    @Test
    fun escapeDismissesLanguageMenuAndReturnsFocus() {
        val app = App(root)
        app.render()
        val trigger = root.querySelector("#$LANGUAGE_MENU_TRIGGER_ID") as HTMLButtonElement
        trigger.click()
        val openTrigger = root.querySelector("#$LANGUAGE_MENU_TRIGGER_ID") as HTMLButtonElement

        document.dispatchEvent(
            js("new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })") as Event,
        )

        assertEquals("false", openTrigger.getAttribute("aria-expanded"))
        assertEquals(LANGUAGE_MENU_TRIGGER_ID, document.activeElement?.id)
    }

    @Test
    fun selectingLanguagePersistsAndAppliesArabicRtlWithoutMotion(): dynamic {
        val browserWindow = kotlinx.browser.window.asDynamic()
        val originalMatchMedia = browserWindow.matchMedia
        browserWindow.matchMedia =
            js("function(query) { return {matches: query === '(prefers-reduced-motion: reduce)'}; }")
        return MainScope().promise {
            try {
                val app = App(root)
                app.render()
                (root.querySelector("#$LANGUAGE_MENU_TRIGGER_ID") as HTMLButtonElement).click()
                (root.querySelector("[role=menuitemradio]") as HTMLButtonElement).click()
                delay(50)

                assertEquals("ar", document.documentElement?.getAttribute("lang"))
                assertEquals("rtl", document.documentElement?.getAttribute("dir"))
                val stored = localStorage.getItem("fukaha_settings")
                assertNotNull(stored)
                assertTrue(stored.contains("\"language\":\"Arabic\""))
                assertNull(root.querySelector("#$LANGUAGE_MENU_ID"))
                assertFalse(document.documentElement?.classList?.contains("language-view-transition") == true)
            } finally {
                browserWindow.matchMedia = originalMatchMedia
            }
        }
    }
}
