package app.fukaha.web

import app.fukaha.AppLanguage
import app.fukaha.AppTheme
import app.fukaha.ShareAction
import kotlinx.browser.localStorage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebSettingsStoreTest {
    @AfterTest
    fun clearStoredSettings() {
        localStorage.removeItem("fukaha_settings")
    }

    @Test
    fun sharingPreferencesSurviveReloadAndUnrelatedEdits(): Promise<Unit> = MainScope().promise {
        val store = WebSettingsStore()
        store.update { it.copy(disabledEmbedPlatforms = setOf("x", "instagram"), useOfficialShortLinks = true, preferredFixers = mapOf("x" to "fixupx.com")) }
        val reopened = WebSettingsStore()
        reopened.update { it.copy(theme = AppTheme.Dark) }
        val restored = reopened.get()
        assertEquals(setOf("x", "instagram"), restored.disabledEmbedPlatforms)
        assertTrue(restored.useOfficialShortLinks)
        assertEquals("fixupx.com", restored.preferredFixers["x"])
    }

    @Test
    fun readsLegacyPayloadAndIgnoresUnknownFields(): Promise<Unit> = MainScope().promise {
        localStorage.setItem(
            "fukaha_settings",
            """{
                "defaultAction":"Clean",
                "preferredFixers":{"x":"fixupx.com"},
                "language":"Spanish",
                "theme":"Dark",
                "removedLegacyField":true
            }""".trimIndent(),
        )

        val settings = WebSettingsStore().get()

        assertEquals(ShareAction.Clean, settings.defaultAction)
        assertEquals(mapOf("x" to "fixupx.com"), settings.preferredFixers)
        assertEquals(AppLanguage.Spanish, settings.language)
        assertEquals(AppTheme.Dark, settings.theme)
        assertFalse(settings.resolveShortLinks)
        assertFalse(settings.useOfficialShortLinks)
        assertTrue(settings.disabledEmbedPlatforms.isEmpty())
    }

    @Test
    fun invalidAndFormerDownloadValuesFallBackSafely(): Promise<Unit> = MainScope().promise {
        localStorage.setItem(
            "fukaha_settings",
            """{"defaultAction":"Download","language":"Klingon","theme":"Neon"}""",
        )

        val settings = WebSettingsStore().get()

        assertEquals(ShareAction.Ask, settings.defaultAction)
        assertEquals(AppLanguage.System, settings.language)
        assertEquals(AppTheme.System, settings.theme)
    }

    @Test
    fun updatePreservesWebSettingsAndOmitsUnsupportedNativeFields(): Promise<Unit> = MainScope().promise {
        val store = WebSettingsStore()
        store.update {
            it.copy(
                defaultAction = ShareAction.Embed,
                language = AppLanguage.Japanese,
                theme = AppTheme.Light,
                resolveShortLinks = true,
            )
        }

        val restored = store.get()
        val raw = localStorage.getItem("fukaha_settings").orEmpty()
        assertEquals(ShareAction.Embed, restored.defaultAction)
        assertEquals(AppLanguage.Japanese, restored.language)
        assertEquals(AppTheme.Light, restored.theme)
        assertFalse(restored.resolveShortLinks)
        assertTrue(raw.contains("\"language\":\"Japanese\""))
        assertFalse(raw.contains("resolveShortLinks"))
    }
}
