package app.fukaha.web

import app.fukaha.AppLanguage
import app.fukaha.AppTheme
import app.fukaha.EmbedHealthKeys
import app.fukaha.EmbedHealthPolicy
import app.fukaha.EmbedHealthProgress
import app.fukaha.EmbedHealthSnapshot
import app.fukaha.EmbedHealthStatus
import app.fukaha.EmbedPreviewResult
import app.fukaha.EmbedService
import app.fukaha.FukahaBridge
import app.fukaha.FukahaSettings
import app.fukaha.PlatformClock
import app.fukaha.PreparedLink
import app.fukaha.ShareAction
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.promise
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.events.EventListener
import kotlin.js.Promise
import kotlin.math.hypot

enum class View { Home, Share, Settings }

private const val STATUS_MS = 2600
private const val OVERLAY_EXIT_MS = 180
private const val MENU_EXIT_MS = 120
private const val SNACKBAR_EXIT_MS = 150
private const val LANGUAGE_TRANSITION_MS = 420L
internal const val LANGUAGE_MENU_ANCHOR_ID = "language-menu-anchor"
internal const val LANGUAGE_MENU_ID = "language-menu"
internal const val LANGUAGE_MENU_TRIGGER_ID = "language-menu-trigger"

class App(private val root: HTMLElement) {
    private val scope: CoroutineScope = MainScope()
    private val bridge = FukahaBridge()
    private val store = WebSettingsStore()
    private val healthStore = WebHealthStore()
    private var previewJob: kotlinx.coroutines.Job? = null
    var previewResult: EmbedPreviewResult? = null
        private set
    var previewChecking = false
        private set
    private var previewUrl: String? = null
    private var previewGeneration = 0
    private val sourcePreferencesStore = WebSourcePreferencesStore()
    var sourcePreferences = sourcePreferencesStore.get()
        private set
    var editingSourceOrder = false
        private set
    var healthAttemptAt: Long? = null
        private set
    var healthAttemptFailed = false
        private set

    var settings: FukahaSettings = FukahaSettings(resolveShortLinks = false)
        private set
    var strings: Strings = Strings.EN
        private set
    var view: View = View.Home
        private set
    var prepared: PreparedLink? = null
        private set
    var status: String? = null
        private set
    var busy: Boolean = false
        private set
    var homeSubmitting: Boolean = false
        private set

    var health: EmbedHealthSnapshot = EmbedHealthSnapshot()
        private set

    /** Non-null while an embedder check is running. */
    var healthProgress: EmbedHealthProgress? = null
        private set
    /** Results from the active run, shown in Settings but not used for link rewriting until valid. */
    private val healthRunStatuses = mutableMapOf<String, EmbedHealthStatus>()

    /** Kept across renders so typing is not lost when the view rebuilds. */
    var draft: String = ""

    /** Platform key whose fixer list is expanded in Settings, if any. */
    var openFixerPlatform: String? = null
        private set

    var languageMenuOpen: Boolean = false
        private set

    private var statusTimer: Int = 0
    private var statusUsesIncrementalRender: Boolean = false
    private var languageMenuPointerListener: EventListener? = null
    private var languageMenuKeyListener: EventListener? = null
    private val exitingElements = mutableSetOf<String>()
    private val exitTimers = mutableMapOf<String, Int>()
    /** Theme and locale both mutate the root snapshot, so they must share one owner. */
    private val appearanceTransitions = AppearanceTransitionGate()
    private var settingsReturnView: View = View.Home
    internal val rootElement: HTMLElement
        get() = root

    fun start(sharedText: String?) {
        // Query only: Chromium needs to know we want gesture-gated clipboard-read before
        // the paste click, otherwise localhost/LAN testing often denies without a prompt.
        Clipboard.askReadPermission()
        installSystemAppearanceListeners()
        scope.launch {
            settings = store.get()
            health = healthStore.get()
            healthAttemptAt = healthStore.lastAttemptAt()
            healthAttemptFailed = healthStore.lastAttemptFailed()
            applyLocaleAndTheme()
            PwaInstall.onChanged { render() }
            if (sharedText.isNullOrBlank()) {
                render()
            } else {
                consumeSharedText(sharedText)
            }
        }
    }

    // region navigation

    fun show(next: View) {
        if (next == View.Settings && view != View.Settings) {
            settingsReturnView = view
        }
        if (view == View.Share && next != View.Share) {
            if (exitElement(".sheet-scrim", OVERLAY_EXIT_MS) { showImmediately(next) }) return
        }
        showImmediately(next)
    }

    private fun showImmediately(next: View) {
        if (next != View.Share) resetPreviewCheck()
        if (view == View.Share && next == View.Home) {
            prepared = null
        }
        view = next
        openFixerPlatform = null
        languageMenuOpen = false
        status = null
        render()
        focusPageTitle()
    }

    /** Settings is reached from wherever the user was, so going back returns there. */
    fun leaveSettings() {
        show(settingsReturnView)
    }

    // endregion

    // region link actions

    fun prepare(text: String) {
        scope.launch { consumeSharedText(text) }
    }

    /**
     * Handles only an explicit submission from Home. Ask keeps the existing options flow, while
     * immediate actions stay on Home and clear the field only after the prepared URL was copied.
     */
    fun submitHome(text: String, submittedDraft: String) {
        if (homeSubmitting) return

        val action = settings.effectiveDefaultAction()
        homeSubmitting = true
        if (action == ShareAction.Ask || action == ShareAction.Download) {
            scope.launch {
                try {
                    consumeSharedText(
                        text = text,
                        failureDraft = submittedDraft,
                        failureMessage = strings.homePrepareFailed,
                    )
                } catch (_: Throwable) {
                    busy = false
                    view = View.Home
                    draft = submittedDraft
                    notify(strings.homePrepareFailed)
                } finally {
                    homeSubmitting = false
                    if (view == View.Home) render()
                }
            }
            return
        }

        render()
        scope.launch {
            val link = try {
                bridge.prepare(text, settings, health.statuses)
            } catch (_: Throwable) {
                null
            }
            if (link == null) {
                homeSubmitting = false
                notify(strings.homePrepareFailed)
                return@launch
            }

            val target = immediateHomeTarget(
                action = action,
                cleanedUrl = link.detected.cleanedUrl,
                embedUrl = link.embedUrl,
            )
            if (target == null) {
                homeSubmitting = false
                render()
                return@launch
            }
            val copied = Clipboard.write(target)
            homeSubmitting = false
            draft = draftAfterImmediateCopy(copied, draft, submittedDraft)
            if (copied) {
                notify(
                    if (action == ShareAction.Clean) {
                        strings.cleanLinkCopied
                    } else {
                        strings.embedLinkCopied
                    },
                )
            } else {
                notify(strings.homeCopyFailed)
            }
        }
    }

    private suspend fun consumeSharedText(
        text: String,
        failureDraft: String? = null,
        failureMessage: String? = null,
    ) {
        resetPreviewCheck()
        busy = true
        view = View.Share
        render()
        focusPageTitle()

        val result = bridge.prepare(text, settings, health.statuses)
        busy = false
        if (result == null) {
            prepared = null
            view = View.Home
            draft = failureDraft ?: text.trim()
            notify(failureMessage ?: strings.noLink)
            focusPageTitle()
            return
        }

        prepared = result
        render()
        focusPageTitle()
        runDefaultAction(result)
    }

    /**
     * `navigator.share` needs a user gesture and a share-target navigation gives us none, so an
     * immediate action copies rather than opening a share sheet that would be rejected. The
     * result stays on screen with a Share button either way.
     */
    private suspend fun runDefaultAction(link: PreparedLink) {
        val target = when (settings.effectiveDefaultAction()) {
            ShareAction.Clean -> link.detected.cleanedUrl
            ShareAction.Embed -> link.embedUrl ?: link.detected.cleanedUrl
            ShareAction.Ask, ShareAction.Download -> return
        }
        if (Clipboard.write(target)) notify(strings.copied)
    }

    /** Called straight from a click handler so the clipboard write keeps its user activation. */
    fun copy(text: String) {
        val promise = Clipboard.start(text)
        if (promise == null) {
            notify(strings.copyFailed)
            return
        }
        promise.then(
            onFulfilled = { notify(strings.copied) },
            onRejected = { notify(strings.copyFailed) },
        )
    }

    /**
     * Gesture-bound for the same reason as [copy], and less forgiving: a read that starts after
     * the click handler has returned is refused even where the permission was already granted.
     * [onText] receives the clipboard text; every other outcome names itself, because "denied"
     * and "empty" ask the user for different things. [onFallback] focuses the field so native
     * paste still works when the programmatic read is refused.
     */
    fun paste(onText: (String) -> Unit, onFallback: () -> Unit = {}) {
        val promise = Clipboard.startRead()
        if (promise == null) {
            onFallback()
            notify(if (Clipboard.isSecure) strings.clipboardFailed else strings.clipboardInsecure)
            return
        }
        promise.then(
            onFulfilled = { text ->
                val trimmed = text.trim()
                if (trimmed.isEmpty()) {
                    onFallback()
                    notify(strings.clipboardEmpty)
                } else {
                    onText(trimmed)
                }
            },
            onRejected = { error ->
                onFallback()
                notify(
                    when {
                        !Clipboard.isSecure -> strings.clipboardInsecure
                        Clipboard.isDenied(error) -> strings.clipboardDenied
                        else -> strings.clipboardFailed
                    },
                )
            },
        )
    }

    /** Also gesture-bound: `navigator.share` rejects if it is called any later. */
    fun share(text: String) {
        val promise = WebShare.start(text)
        if (promise == null) {
            // No Web Share API in this browser, so copying is the useful equivalent.
            copy(text)
            return
        }
        promise.then(
            onFulfilled = { },
            onRejected = { error ->
                // A dismissed sheet was a deliberate choice; only a real failure falls back.
                if (!WebShare.isDismissal(error)) copy(text)
            },
        )
    }

    /** Gesture-bound like [copy]: the install prompt is refused without live user activation. */
    fun install() {
        val promise = PwaInstall.start() ?: return
        promise.then(
            onFulfilled = { render() },
            onRejected = { render() },
        )
    }

    // endregion

    // region settings

    fun toggleSourceOrder() {
        editingSourceOrder = !editingSourceOrder
        render()
        (root.querySelector("#source-order-toggle") as? HTMLElement)?.focus()
    }

    fun changeSourcePreferences(next: WebSourcePreferences, focusId: String) {
        val scroll = (root.querySelector(".picker-options") as? HTMLElement)?.scrollTop
        if (!sourcePreferencesStore.save(next)) {
            notify(strings.storageUnavailable, incrementally = true)
            return
        }
        sourcePreferences = next
        render()
        val control = document.getElementById(focusId) as? HTMLElement
        // The move button may become disabled at the boundary; retain focus on the star instead.
        if (control?.hasAttribute("disabled") == true) {
            (control.parentElement?.querySelector("[data-source-favorite]") as? HTMLElement)?.focus()
        } else control?.focus()
        scroll?.let { (root.querySelector(".picker-options") as? HTMLElement)?.scrollTop = it }
    }

    fun toggleFixerPicker(platformKey: String?) {
        val closing = openFixerPlatform != null &&
            (platformKey == null || openFixerPlatform == platformKey)
        if (closing) {
            if (exitElement(".picker-scrim", OVERLAY_EXIT_MS) {
                    openFixerPlatform = null
                    render()
                }
            ) {
                return
            }
        }
        editingSourceOrder = false
        openFixerPlatform = platformKey
        render()
        if (openFixerPlatform != null) {
            (document.getElementById("fixer-picker-title") as? HTMLElement)?.focus()
        }
    }

    fun toggleLanguageMenu() {
        if (appearanceTransitions.isActive) return
        languageMenuOpen = !languageMenuOpen
        render()
    }

    private fun dismissLanguageMenu(
        returnFocus: Boolean = false,
        afterDismiss: (() -> Unit)? = null,
    ) {
        if (!languageMenuOpen) {
            afterDismiss?.invoke()
            return
        }
        languageMenuOpen = false
        removeLanguageMenuListeners()
        (document.getElementById(LANGUAGE_MENU_TRIGGER_ID) as? HTMLElement)?.let { trigger ->
            trigger.setAttribute("aria-expanded", "false")
            if (returnFocus) trigger.focus()
        }
        if (!exitElement("#$LANGUAGE_MENU_ID", MENU_EXIT_MS) {
                document.getElementById(LANGUAGE_MENU_ID)?.remove()
                afterDismiss?.invoke()
            }
        ) {
            afterDismiss?.invoke()
        }
    }

    private fun installLanguageMenuListeners() {
        if (!languageMenuOpen) return
        val anchor = document.getElementById(LANGUAGE_MENU_ANCHOR_ID) ?: return

        languageMenuPointerListener = EventListener { event ->
            val target = event.target as? Node
            if (target == null || !anchor.contains(target)) dismissLanguageMenu()
        }.also { document.addEventListener("pointerdown", it, true) }

        languageMenuKeyListener = EventListener { event ->
            if (event.asDynamic().key == "Escape") {
                event.preventDefault()
                dismissLanguageMenu(returnFocus = true)
            }
        }.also { document.addEventListener("keydown", it, true) }
    }

    private fun removeLanguageMenuListeners() {
        languageMenuPointerListener?.let {
            document.removeEventListener("pointerdown", it, true)
        }
        languageMenuKeyListener?.let {
            document.removeEventListener("keydown", it, true)
        }
        languageMenuPointerListener = null
        languageMenuKeyListener = null
    }

    fun selectLanguage(language: AppLanguage) {
        if (appearanceTransitions.isActive) return
        if (settings.language != language && !appearanceTransitions.acquire()) return
        dismissLanguageMenu {
            if (settings.language == language) {
                (document.getElementById(LANGUAGE_MENU_TRIGGER_ID) as? HTMLElement)?.focus()
            } else {
                changeLanguage(language)
            }
        }
    }

    fun toggleTheme(origin: HTMLElement) {
        val systemDark = window.matchMedia("(prefers-color-scheme: dark)").matches
        changeTheme(origin, settings.theme.toggled(systemDark))
    }

    fun changeTheme(origin: HTMLElement, next: AppTheme) {
        if (settings.theme == next) return
        if (!appearanceTransitions.acquire()) return

        val reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
        val page = document.documentElement as? HTMLElement
        if (reduceMotion || page == null) {
            scope.launch {
                try {
                    applyTheme(next)
                } finally {
                    appearanceTransitions.release()
                }
            }
            return
        }

        val bounds = origin.getBoundingClientRect()
        val centerX = bounds.left + bounds.width / 2.0
        val centerY = bounds.top + bounds.height / 2.0
        val radius = hypot(
            maxOf(centerX, window.innerWidth - centerX),
            maxOf(centerY, window.innerHeight - centerY),
        )
        val canViewTransition = shouldUseViewTransitions(
            apiAvailable = jsTypeOf(document.asDynamic().startViewTransition) == "function",
            webKitEngine = Platform.isWebKitEngine,
        )

        if (canViewTransition) {
            page.style.setProperty("--theme-origin-x", "${centerX}px")
            page.style.setProperty("--theme-origin-y", "${centerY}px")
            page.style.setProperty("--theme-reveal-radius", "${radius}px")
            page.classList.add("theme-view-transition")

            val transition: dynamic = runCatching {
                document.asDynamic().startViewTransition {
                    scope.promise { applyTheme(next) }
                }
            }.getOrNull()
            if (transition != null) {
                var cleared = false
                val finish = {
                    if (!cleared) {
                        cleared = true
                        clearThemeTransition(page)
                    }
                }
                val timeout = window.setTimeout({ finish() }, 2_000)
                scope.launch {
                    runCatching { (transition.finished as Promise<*>).await() }
                    window.clearTimeout(timeout)
                    finish()
                }
                return
            }
            clearThemeTransition(page, release = false)
        }

        // Safari and other unsupported browsers crossfade a frozen, inert copy of the old UI.
        // Its inherited colour tokens are pinned before the live document changes, avoiding the
        // full-page variable swap that looked like a flash.
        origin.classList.add("theme-triggering")
        val snapshot = createThemeFallbackSnapshot(page)
        scope.launch {
            try {
                delay(120)
                applyTheme(next)
                delay(500)
            } finally {
                snapshot?.remove()
                origin.classList.remove("theme-triggering")
                appearanceTransitions.release()
            }
        }
    }

    private fun createThemeFallbackSnapshot(page: HTMLElement): HTMLElement? {
        val body = document.body ?: return null
        val snapshot = document.createElement("div") as HTMLElement
        snapshot.className = "theme-fallback-snapshot"
        snapshot.setAttribute("aria-hidden", "true")
        snapshot.asDynamic().inert = true

        // One computed-style read captures every custom token; descendants inherit the frozen
        // values without walking the cloned tree or forcing repeated layout.
        val computed = window.getComputedStyle(page)
        snapshot.style.setProperty("color-scheme", computed.getPropertyValue("color-scheme"))
        for (index in 0 until computed.length) {
            val property = computed.item(index)
            if (property.startsWith("--")) {
                snapshot.style.setProperty(property, computed.getPropertyValue(property))
            }
        }

        val content = root.cloneNode(deep = true) as HTMLElement
        content.removeAttribute("id")
        content.classList.add("theme-fallback-snapshot-content")
        snapshot.appendChild(content)
        body.appendChild(snapshot)
        snapshot.scrollTop = window.scrollY
        return snapshot
    }

    private suspend fun applyTheme(next: AppTheme) {
        applySettingsUpdate { current -> current.copy(theme = next) }
    }

    private fun clearThemeTransition(page: HTMLElement, release: Boolean = true) {
        page.classList.remove("theme-view-transition")
        page.style.removeProperty("--theme-origin-x")
        page.style.removeProperty("--theme-origin-y")
        page.style.removeProperty("--theme-reveal-radius")
        document.querySelector(".theme-fallback-snapshot")?.let {
            it.parentNode?.removeChild(it)
        }
        if (release) appearanceTransitions.release()
    }

    private fun changeLanguage(language: AppLanguage) {
        val reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
        val page = document.documentElement as? HTMLElement
        val trigger = document.getElementById(LANGUAGE_MENU_TRIGGER_ID) as? HTMLElement
        if (reduceMotion || page == null) {
            scope.launch {
                try {
                    applyLanguage(language)
                } finally {
                    appearanceTransitions.release()
                }
            }
            return
        }

        val bounds = trigger?.getBoundingClientRect()
        val originX = bounds?.let { it.left + it.width / 2.0 } ?: window.innerWidth / 2.0
        val originY = bounds?.let { it.top + it.height / 2.0 } ?: 48.0
        page.style.setProperty("--language-origin-x", "${originX}px")
        page.style.setProperty("--language-origin-y", "${originY}px")
        page.classList.add("language-view-transition")

        val canViewTransition = shouldUseViewTransitions(
            apiAvailable = jsTypeOf(document.asDynamic().startViewTransition) == "function",
            webKitEngine = Platform.isWebKitEngine,
        )
        if (canViewTransition) {
            val transition: dynamic = runCatching {
                document.asDynamic().startViewTransition {
                    scope.promise { applyLanguage(language) }
                }
            }.getOrNull()
            if (transition != null) {
                var cleared = false
                val finish = {
                    if (!cleared) {
                        cleared = true
                        clearLanguageTransition(page)
                    }
                }
                val timeout = window.setTimeout({ finish() }, 2_000)
                scope.launch {
                    runCatching { (transition.finished as Promise<*>).await() }
                    window.clearTimeout(timeout)
                    finish()
                }
                return
            }
        }

        page.classList.remove("language-view-transition")
        val snapshot = createLanguageFallbackSnapshot(page)
        scope.launch {
            try {
                applyLanguage(language)
                delay(LANGUAGE_TRANSITION_MS)
            } finally {
                snapshot?.remove()
                clearLanguageTransition(page)
            }
        }
    }

    private fun createLanguageFallbackSnapshot(page: HTMLElement): HTMLElement? {
        val body = document.body ?: return null
        val snapshot = document.createElement("div") as HTMLElement
        snapshot.className = "language-fallback-snapshot"
        snapshot.setAttribute("aria-hidden", "true")
        snapshot.setAttribute("dir", page.getAttribute("dir") ?: "ltr")
        snapshot.asDynamic().inert = true

        val content = root.cloneNode(deep = true) as HTMLElement
        content.removeAttribute("id")
        content.classList.add("language-fallback-snapshot-content")
        snapshot.appendChild(content)
        body.appendChild(snapshot)
        snapshot.scrollTop = window.scrollY
        return snapshot
    }

    private suspend fun applyLanguage(language: AppLanguage) {
        // A single store transaction is the only persistence point for this selection.
        applySettingsUpdate { current -> current.copy(language = language) }
        (document.getElementById(LANGUAGE_MENU_TRIGGER_ID) as? HTMLElement)?.focus()
    }

    private fun clearLanguageTransition(page: HTMLElement) {
        page.classList.remove("language-view-transition")
        page.style.removeProperty("--language-origin-x")
        page.style.removeProperty("--language-origin-y")
        document.querySelector(".language-fallback-snapshot")?.let {
            it.parentNode?.removeChild(it)
        }
        appearanceTransitions.release()
    }

    fun updateSettings(
        afterRender: (() -> Unit)? = null,
        transform: (FukahaSettings) -> FukahaSettings,
    ) {
        scope.launch {
            if (applySettingsUpdate(transform)) afterRender?.invoke()
        }
    }

    private suspend fun applySettingsUpdate(transform: (FukahaSettings) -> FukahaSettings): Boolean {
        try {
            store.update(transform)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Throwable) {
            notify(strings.storageUnavailable, incrementally = true)
            return false
        }
        settings = store.get()
        applyLocaleAndTheme()
        recomputePrepared()
        render()
        return true
    }

    // endregion

    // region embedder health

    /**
     * Manual only. Probing contacts every fixer host from the user's browser, so it never runs
     * unprompted the way the Android app's six-hourly refresh does.
     */
    fun checkEmbedders() {
        if (healthProgress != null) {
            notify(strings.embedHealthBusy)
            return
        }
        val cooldown = EmbedHealthPolicy.cooldownRemainingMs(health.checkedAtEpochMs)
        if (cooldown > 0L) {
            notify(strings.embedHealthCooldownToast(minutesOf(cooldown)))
            return
        }

        scope.launch {
            val hosts = uniqueFixerHosts()
            healthRunStatuses.clear()
            updateHealthProgress(
                EmbedHealthProgress(
                    currentHost = hosts.firstOrNull().orEmpty(),
                    currentIndex = 0,
                    total = hosts.size,
                ),
            )

            val results = try {
                WebEmbedHealth.refresh(hosts) { progress, result ->
                    updateHealthProgress(progress, result)
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                healthProgress = null
                healthRunStatuses.clear()
                updateEmbedHealthUi(this@App)
                throw error
            } catch (_: Throwable) {
                emptyMap()
            }
            healthProgress = null
            healthRunStatuses.clear()
            val now = PlatformClock.epochMillis()
            healthAttemptAt = now
            healthAttemptFailed = !EmbedHealthPolicy.isUsableResult(results)
            val saved = healthStore.save(results, now)
            if (!healthAttemptFailed) {
                health = EmbedHealthSnapshot(results, now)
                recomputePrepared()
            }
            if (!updateEmbedHealthUi(this@App) && view != View.Settings) render()
            val message = when {
                !saved -> strings.storageUnavailable
                healthAttemptFailed -> strings.embedHealthOffline
                else -> strings.embedHealthCounts(health.aliveCount, health.deadCount, health.unknownCount)
            }
            notify(message, incrementally = true)
        }
    }

    internal fun updateHealthProgress(
        progress: EmbedHealthProgress,
        completedStatus: EmbedHealthStatus? = null,
    ) {
        healthProgress = progress
        if (completedStatus != null) {
            healthRunStatuses[EmbedHealthKeys.normalize(progress.currentHost)] = completedStatus
        }
        updateEmbedHealthUi(this)
    }

    private fun resetPreviewCheck() {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
        previewChecking = false
        previewResult = null
        previewUrl = null
    }

    fun checkPreview() {
        val link = prepared ?: return
        val url = link.embedUrl ?: return
        if (previewChecking) return
        val generation = ++previewGeneration
        previewUrl = url
        previewChecking = true
        previewResult = null
        render()
        previewJob = scope.launch {
            try {
                val result = WebPreviewChecker.check(link.detected.cleanedUrl, url)
                if (previewGeneration == generation && previewUrl == url && prepared?.embedUrl == url) previewResult = result
            } finally {
                if (previewGeneration == generation && previewUrl == url) {
                    previewChecking = false
                    if (view == View.Share) {
                        render()
                        (root.querySelector("#preview-check") as? HTMLElement)?.focus()
                    }
                }
            }
        }
    }

    fun statusOf(host: String): EmbedHealthStatus =
        healthRunStatuses[EmbedHealthKeys.normalize(host)] ?: health.statusOf(host)

    private fun uniqueFixerHosts(): List<String> =
        bridge.platformKeys()
            .flatMap { bridge.servicesFor(it) }
            .map { EmbedHealthKeys.normalize(it.normalizedHost()) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    fun minutesOf(millis: Long): Int = ((millis + 59_999L) / 60_000L).toInt()

    // endregion

    /** A settings or health change can move the embed link, so recompute what is on screen. */
    private suspend fun recomputePrepared() {
        val current = prepared ?: return
        prepared = bridge.prepare(current.detected.originalUrl, settings, health.statuses)
        if (prepared?.embedUrl != current.embedUrl) resetPreviewCheck()
    }

    /** Shows a transient message in the live region, replacing any message still on screen. */
    fun notify(message: String, incrementally: Boolean = false) {
        window.clearTimeout(statusTimer)
        cancelExit(".snackbar-visible")
        status = message
        statusUsesIncrementalRender = incrementally
        if (incrementally) updateSnackbar() else render()
        statusTimer = window.setTimeout({ dismissStatus() }, STATUS_MS)
    }

    private fun dismissStatus() {
        if (status == null) return
        if (!exitElement(".snackbar-visible", SNACKBAR_EXIT_MS) {
                status = null
                if (statusUsesIncrementalRender) updateSnackbar() else render()
            }
        ) {
            status = null
            if (statusUsesIncrementalRender) updateSnackbar() else render()
        }
    }

    /** Health completion owns no page state outside its region and this persistent live region. */
    private fun updateSnackbar() {
        val snackbar = root.querySelector(".snackbar") as? HTMLElement ?: return
        snackbar.className = if (status != null) "snackbar snackbar-visible" else "snackbar"
        snackbar.textContent = status.orEmpty()
    }

    /**
     * Keeps transient UI mounted just long enough to paint its exit. Reduced-motion users
     * complete synchronously, and the set prevents repeated taps/Escape from queuing callbacks.
     */
    private fun exitElement(
        selector: String,
        durationMs: Int,
        onFinished: () -> Unit,
    ): Boolean {
        val element = document.querySelector(selector) as? HTMLElement ?: return false
        if (!exitingElements.add(selector)) return true
        element.classList.add("motion-exit")
        val finish = {
            exitingElements.remove(selector)
            exitTimers.remove(selector)
            onFinished()
        }
        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            finish()
        } else {
            exitTimers[selector] = window.setTimeout(finish, durationMs)
        }
        return true
    }

    private fun cancelExit(selector: String) {
        exitTimers.remove(selector)?.let { window.clearTimeout(it) }
        exitingElements.remove(selector)
    }

    /**
     * System language/theme are CSS- and navigator-driven. iOS PWAs often only refresh those
     * signals when the app becomes visible again, so re-read them on languagechange, pageshow,
     * and visibility — and drop any leftover theme/language overlay that Safari left behind.
     */
    private fun installSystemAppearanceListeners() {
        val onLanguageChange = EventListener { syncSystemAppearance() }
        window.addEventListener("languagechange", onLanguageChange)

        val themeMedia = window.matchMedia("(prefers-color-scheme: dark)")
        val onThemeChange = EventListener { syncSystemAppearance() }
        if (jsTypeOf(themeMedia.asDynamic().addEventListener) == "function") {
            themeMedia.asDynamic().addEventListener("change", onThemeChange)
        } else {
            themeMedia.asDynamic().addListener(onThemeChange)
        }

        val onForeground = EventListener {
            if (document.asDynamic().visibilityState == "hidden") return@EventListener
            clearStaleAppearanceOverlays()
            syncSystemAppearance()
        }
        window.addEventListener("pageshow", onForeground)
        document.addEventListener("visibilitychange", onForeground)
    }

    private fun syncSystemAppearance() {
        if (settings.language != AppLanguage.System && settings.theme != AppTheme.System) return
        val previous = strings
        applyLocaleAndTheme()
        if (previous !== strings) render()
    }

    private fun clearStaleAppearanceOverlays() {
        listOf(".theme-fallback-snapshot", ".language-fallback-snapshot").forEach { selector ->
            document.querySelector(selector)?.let { it.parentNode?.removeChild(it) }
        }
        val page = document.documentElement as? HTMLElement ?: return
        if (page.classList.contains("theme-view-transition")) {
            page.classList.remove("theme-view-transition")
            page.style.removeProperty("--theme-origin-x")
            page.style.removeProperty("--theme-origin-y")
            page.style.removeProperty("--theme-reveal-radius")
        }
        if (page.classList.contains("language-view-transition")) {
            page.classList.remove("language-view-transition")
            page.style.removeProperty("--language-origin-x")
            page.style.removeProperty("--language-origin-y")
        }
        appearanceTransitions.release()
    }

    private fun applyLocaleAndTheme() {
        strings = Strings.forLanguage(settings.language)
        document.documentElement?.setAttribute("lang", Strings.languageTag(settings.language))
        document.documentElement?.setAttribute(
            "dir",
            if (Strings.isArabic(settings.language)) "rtl" else "ltr",
        )
        when (settings.theme) {
            AppTheme.System -> document.documentElement?.removeAttribute("data-theme")
            AppTheme.Light -> document.documentElement?.setAttribute("data-theme", "light")
            AppTheme.Dark -> document.documentElement?.setAttribute("data-theme", "dark")
        }
        document.title = strings.appName
    }

    fun render() {
        removeLanguageMenuListeners()
        root.clear()
        renderApp(this, root)
        installLanguageMenuListeners()
        document.getElementById("boot-splash")?.remove()
    }

    /** Programmatic views behave like pages: announce their title without adding a tab stop. */
    private fun focusPageTitle() {
        (document.getElementById("page-title") as? HTMLElement)?.focus()
    }

    fun catalogPlatformKeys(): List<String> = bridge.platformKeys()

    fun servicesFor(platformKey: String) = sourcePreferences.sorted(platformKey, bridge.servicesFor(platformKey))

    /** The user's stored fixer for [platformKey], falling back to the catalog default. */
    fun pickedFixer(platformKey: String): EmbedService? =
        bridge.serviceForHost(platformKey, chosenFixerHost(platformKey))

    /** Automatic choice uses health; an explicit choice stays selected. */
    fun effectiveFixer(platformKey: String): EmbedService? =
        bridge.effectiveService(platformKey, settings.preferredFixers[platformKey], health.statuses)

    private fun chosenFixerHost(platformKey: String): String? =
        settings.preferredFixers[platformKey] ?: bridge.defaultFixer(platformKey)
}

fun main() {
    val root = document.getElementById("app") as? HTMLElement ?: return
    val startup = readStartupNavigation()
    registerServiceWorker()
    installOutsideTapFocusDismissal()
    App(root).start(startup.sharedText)
}

/**
 * Web Share Target hands us the link in `url`, `text`, or `title` depending on the sending
 * app — Chrome on Android usually uses `text` for a shared page, not `url`. Everything is
 * concatenated and left to `UrlCleaner.extractFirstUrl`, which already digs a URL out of
 * surrounding prose. The values are captured before replacing an arbitrary document pathname,
 * and only consumed share fields are removed from the canonical root URL.
 */
private fun readStartupNavigation(): StartupNavigation {
    val startup = resolveStartupNavigation(
        pathname = window.location.pathname,
        search = window.location.search,
        hash = window.location.hash,
    )
    if (startup.shouldReplace) {
        // Replacing (rather than pushing) keeps Back pointed at the page that launched Fukaha.
        window.history.replaceState(null, "", startup.canonicalUrl)
    }
    return startup
}

private fun registerServiceWorker() {
    val host = window.location.hostname
    if (host == "localhost" || host == "127.0.0.1") return
    window.addEventListener("load", {
        window.navigator.asDynamic().serviceWorker?.register("/sw.js")
    })
}
