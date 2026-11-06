package app.fukaha.web

import app.fukaha.EmbedHealthKeys
import app.fukaha.EmbedHealthPolicy
import app.fukaha.EmbedHealthStatus
import app.fukaha.EmbedService
import app.fukaha.ShareAction
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement

internal const val EMBED_HEALTH_SUMMARY_ID = "embed-health-summary"
internal const val EMBED_HEALTH_PROGRESS_ID = "embed-health-progress"
private const val EMBED_HEALTH_REFRESH_ID = "embed-health-refresh"

fun renderSettings(app: App, parent: HTMLElement) {
    val s = app.strings

    renderDefaultAction(app, parent)
    parent.preferenceToggle("official-short-links", s.officialShortLinks, s.officialShortLinksHint, app.settings.useOfficialShortLinks) { enabled ->
        app.updateSettings(afterRender = { (document.getElementById("official-short-links") as? HTMLElement)?.focus() }) { it.copy(useOfficialShortLinks = enabled) }
    }
    renderEmbedders(app, parent)
    renderAbout(app, parent)

    parent.el("details", "disclosure") {
        el("summary", null) {
            icon(Icon.INFO, "icon icon-sm")
            el("span", null, s.webLimitsTitle)
            icon(Icon.EXPAND)
        }
        el("div", "disclosure-body") {
            el("ul", "bullets") {
                el("li", null, s.shortLinkNote)
                el("li", null, s.downloadNote)
            }
        }
    }

}

private fun renderDefaultAction(app: App, parent: HTMLElement) {
    val s = app.strings
    parent.el("section", "section settings-first-section") {
        el("h2", "title-small section-header", s.sectionSharing)
        el("div", "card settings-surface action-surface") {
            el("p", "body-small on-surface-variant section-hint", s.defaultActionHint)
            el("div", "action-list") {
                setAttribute("role", "radiogroup")
                setAttribute("aria-label", s.sectionSharing)
                radioItem(
                    s.actionAsk,
                    s.actionAskHint,
                    Icon.SAMPLE,
                    s.selected,
                    "default-action",
                    app.settings.defaultAction == ShareAction.Ask,
                ) {
                    app.updateSettings { it.copy(defaultAction = ShareAction.Ask) }
                }
                radioItem(
                    s.actionClean,
                    s.actionCleanHint,
                    Icon.CLEAN,
                    s.selected,
                    "default-action",
                    app.settings.defaultAction == ShareAction.Clean,
                ) {
                    app.updateSettings { it.copy(defaultAction = ShareAction.Clean) }
                }
                radioItem(
                    s.actionEmbed,
                    s.actionEmbedHint,
                    Icon.PREVIEW,
                    s.selected,
                    "default-action",
                    app.settings.defaultAction == ShareAction.Embed,
                ) {
                    app.updateSettings { it.copy(defaultAction = ShareAction.Embed) }
                }
            }
        }
    }
}

private fun renderEmbedders(app: App, parent: HTMLElement) {
    val s = app.strings
    parent.el("section", "section") {
        el("h2", "title-small section-header", s.preferredFixers)

        el("div", "card settings-surface") {
            el("div", "embed-health-row") {
                el("div", "list-item-text") {
                    el("span", "body-large", s.embedHealthTitle)
                    el("span", "body-small on-surface-variant", healthSummary(app)) {
                        id = EMBED_HEALTH_SUMMARY_ID
                    }
                }
                iconButton(Icon.REFRESH, s.embedHealthRefresh) {
                    app.checkEmbedders()
                }.also {
                    it.id = EMBED_HEALTH_REFRESH_ID
                    it.disabled = app.healthProgress != null
                }
            }

            el("div", "progress embed-health-progress") {
                id = EMBED_HEALTH_PROGRESS_ID
                setAttribute("role", "progressbar")
                setAttribute("aria-valuemin", "0")
                val progress = app.healthProgress
                if (progress == null) {
                    setAttribute("hidden", "")
                } else {
                    setAttribute("aria-valuemax", progress.total.toString())
                    setAttribute("aria-valuenow", progress.completedCount.toString())
                }
                el("div", "progress-bar") {
                    style.width = "100%"
                    style.transform = "scaleX(${progress?.fraction ?: 0.0})"
                }
            }

            el("p", "body-small on-surface-variant source-check-hint", s.embedHealthHint)
            el("div", "divider")
            el("div", "fixer-list") {
                app.catalogPlatformKeys().forEach { key ->
                    val services = app.servicesFor(key)
                    if (services.isEmpty()) return@forEach
                    renderFixerRow(app, this, key)
                }
            }
        }
    }

    app.openFixerPlatform?.let { platformKey ->
        parent.el("div", "picker-scrim") {
            onclick = { event ->
                if (event.target == event.currentTarget) app.toggleFixerPicker(null)
                Unit
            }
            el("section", "picker-sheet") {
                setAttribute("role", "dialog")
                setAttribute("aria-modal", "true")
                setAttribute("aria-labelledby", "fixer-picker-title")
                onkeydown = { event ->
                    if (event.asDynamic().key == "Escape") app.toggleFixerPicker(null)
                    Unit
                }
                el("div", "sheet-handle") { setAttribute("aria-hidden", "true") }
                el("div", "picker-head") {
                    iconButton(Icon.BACK, s.back, "icon-flip") { app.toggleFixerPicker(null) }
                    el("div", "picker-head-copy") {
                        el("h2", "title-medium", s.fixerChooseTitle) {
                            id = "fixer-picker-title"
                            setAttribute("tabindex", "-1")
                        }
                        el(
                            "p",
                            "body-small on-surface-variant",
                            s.fixerPickSubtitle(platformLabel(platformKey)),
                        )
                    }
                }
                el("div", "source-toolbar") {
                    el("p", "body-small on-surface-variant", s.sourceSavedHint)
                    button(if (app.editingSourceOrder) s.sourceDone else s.sourceOrganize, ButtonStyle.Text) {
                        app.toggleSourceOrder()
                    }.also {
                        it.id = "source-order-toggle"
                        it.setAttribute("aria-pressed", app.editingSourceOrder.toString())
                    }
                }
                el("div", "picker-options") {
                    val services = app.servicesFor(platformKey)
                    val picked = app.pickedFixer(platformKey)
                    val inUse = app.effectiveFixer(platformKey)
                    val pickedLabel = if (app.settings.preferredFixers.containsKey(platformKey)) {
                        s.fixerYourPick
                    } else {
                        s.fixerCatalogDefault
                    }
                    var previousFavorite: Boolean? = null
                    services.forEach { service ->
                        val favorite = app.sourcePreferences.isFavorite(platformKey, service.normalizedHost())
                        if (previousFavorite != favorite) {
                            el("h3", "title-small source-group-title", if (favorite) s.sourceFavorites else s.sourceOthers)
                            previousFavorite = favorite
                        }
                        renderFixerOption(
                            app = app,
                            parent = this,
                            platformKey = platformKey,
                            service = service,
                            isInUse = service == inUse,
                            isPicked = service == picked,
                            pickedLabel = pickedLabel,
                        )
                    }
                }
            }
        }
    }
}

private fun healthSummary(app: App): String {
    val s = app.strings
    app.healthProgress?.let { progress ->
        return s.embedHealthProgress(progress.completedCount, progress.total)
    }
    val attemptedAt = app.healthAttemptAt ?: app.health.checkedAtEpochMs ?: return s.embedHealthNever
    val date = kotlin.js.Date(attemptedAt.toDouble()).toLocaleString()
    val lastCheck = "${s.embedHealthLastAttempt}: $date"
    if (app.healthAttemptFailed) return "$lastCheck · ${s.embedHealthOffline}"
    val counts = s.embedHealthCounts(app.health.aliveCount, app.health.deadCount, app.health.unknownCount)
    return "$lastCheck · $counts"
}

private fun renderFixerRow(
    app: App,
    parent: HTMLElement,
    platformKey: String,
) {
    parent.el("div", "fixer-list-row") {
        el("button", "fixer-row state") {
            setAttribute("type", "button")
            setAttribute("data-fixer-row", platformKey)
            renderFixerRowContents(app, this, platformKey)
            onclick = { app.toggleFixerPicker(platformKey); Unit }
        }
        el("label", "fixer-embed-switch") {
            val label = "${app.strings.embedLinksEnabled} · ${platformLabel(platformKey)}"
            setAttribute("title", app.strings.embedLinksEnabledHint)
            el("span", "sr-only", label)
            val input = el("input", "preference-checkbox") {
                id = "embed-enabled-$platformKey"
                setAttribute("type", "checkbox")
                setAttribute("role", "switch")
            } as HTMLInputElement
            input.checked = platformKey !in app.settings.disabledEmbedPlatforms
            input.onchange = {
                val enabled = input.checked
                app.updateSettings(afterRender = { (document.getElementById("embed-enabled-$platformKey") as? HTMLElement)?.focus() }) {
                    it.copy(disabledEmbedPlatforms = if (enabled) it.disabledEmbedPlatforms - platformKey else it.disabledEmbedPlatforms + platformKey)
                }
                Unit
            }
        }
    }
}

private fun renderFixerRowContents(app: App, row: HTMLElement, platformKey: String) {
    val s = app.strings
    // The effective source matches sharing, including automatic choices when no pick is saved.
    val picked = app.pickedFixer(platformKey)
    val inUse = app.effectiveFixer(platformKey)
    val standingInFor = picked?.takeIf { inUse != null && inUse != it }
    row.setAttribute("data-health-state", fixerRowHealthState(app, platformKey))

    inUse?.let { row.statusDot(app, it) }
    row.el("div", "list-item-text") {
        el(
            "span",
            "title-small",
            "${platformLabel(platformKey)} · ${inUse?.name ?: s.preferredFixerUnknown}",
        )
        inUse?.let {
            el(
                "span",
                "body-small on-surface-variant",
                EmbedHealthKeys.displayHost(it.normalizedHost()),
            )
        }
        standingInFor?.let {
            el("span", "body-small on-surface-variant", s.fixerStandIn(it.name))
        }
    }
    row.el("div", "list-item-trailing") {
        inUse?.let {
            healthLabel(app, it).takeIf { label -> label.isNotBlank() }?.let { label ->
                el("span", "sr-only", label)
            }
        }
        icon(Icon.CHEVRON, "icon icon-sm icon-flip")
    }
}

private fun fixerRowHealthState(app: App, platformKey: String): String {
    val picked = app.pickedFixer(platformKey)
    val inUse = app.effectiveFixer(platformKey)
    return listOf(
        picked?.normalizedHost().orEmpty(),
        inUse?.normalizedHost().orEmpty(),
        inUse?.let { app.statusOf(it.normalizedHost()).name }.orEmpty(),
    ).joinToString("|")
}

private fun renderFixerOption(
    app: App,
    parent: HTMLElement,
    platformKey: String,
    service: EmbedService,
    isInUse: Boolean,
    isPicked: Boolean,
    pickedLabel: String,
) {
    val s = app.strings
    val host = service.normalizedHost()
    val favorite = app.sourcePreferences.isFavorite(platformKey, host)
    val controlId = "source-$platformKey-${EmbedHealthKeys.displayHost(host)}"
    parent.el("div", "source-option-row") {
        setAttribute("data-source-host", host)
        el("div", "source-option-main") {
            el("button", if (isInUse) "fixer-option fixer-option-selected state" else "fixer-option state") {
                setAttribute("type", "button")
                setAttribute("aria-pressed", isInUse.toString())
                if (isInUse) setAttribute("aria-current", "true")
                if (isInUse) {
                    icon(Icon.CHECK, "icon fixer-radio fixer-radio-selected")
                } else {
                    el("span", "fixer-radio")
                }
                el("div", "list-item-text") {
                    el("span", "title-small", service.name)
                    el("span", "fixer-host") {
                        dir = "ltr"
                        textContent = EmbedHealthKeys.displayHost(service.normalizedHost())
                    }
                    service.description?.takeIf { it.isNotBlank() }?.let {
                        el("span", "body-small on-surface-variant fixer-description", it)
                    }
                    service.author?.let { el("span", "body-small on-surface-variant", s.fixerByAuthor(it)) }
                }
                el("div", "list-item-trailing") {
                    if (isPicked && !isInUse) el("span", "chip chip-small chip-outline", pickedLabel)
                    if (isInUse) el("span", "chip chip-small action-selected-chip", s.selected)
                    el("span", healthLabelClasses(app, service), healthLabel(app, service)) {
                        setAttribute("data-fixer-health-host", service.normalizedHost())
                    }
                }
                onclick = {
                    app.updateSettings(
                        transform = { current ->
                            current.copy(
                                preferredFixers = current.preferredFixers +
                                    (platformKey to service.normalizedHost()),
                            )
                        },
                        afterRender = { app.toggleFixerPicker(null) },
                    )
                    Unit
                }
            }
        }
        el("div", "source-option-controls") {
            iconButton(Icon.FAVORITE, "${if (favorite) s.sourceFavoriteRemove else s.sourceFavoriteAdd}: ${service.name}",
                if (favorite) "source-favorite-active" else "") {
                app.changeSourcePreferences(app.sourcePreferences.toggleFavorite(platformKey, host), "$controlId-star")
            }.also {
                it.id = "$controlId-star"
                it.setAttribute("data-source-favorite", host)
                it.setAttribute("aria-pressed", favorite.toString())
            }
            if (app.editingSourceOrder) {
                val group = app.servicesFor(platformKey).filter {
                    app.sourcePreferences.isFavorite(platformKey, it.normalizedHost()) == favorite
                }
                val index = group.indexOf(service)
                listOf(-1, 1).forEach { direction ->
                    val label = if (direction < 0) s.sourceMoveUp else s.sourceMoveDown
                    button(if (direction < 0) "↑" else "↓", ButtonStyle.Text, extraClasses = "source-move") {
                        app.changeSourcePreferences(
                            app.sourcePreferences.move(platformKey, host, direction, app.servicesFor(platformKey)),
                            "$controlId-move-$direction",
                        )
                    }.also {
                        it.id = "$controlId-move-$direction"
                        it.setAttribute("aria-label", "$label: ${service.name}")
                        it.setAttribute("title", "$label: ${service.name}")
                        it.disabled = if (direction < 0) index <= 0 else index >= group.lastIndex
                    }
                }
            }
        }
    }
}

private fun HTMLElement.statusDot(app: App, service: EmbedService) {
    val classes = when (app.statusOf(service.normalizedHost())) {
        EmbedHealthStatus.Alive -> "status-dot status-alive"
        EmbedHealthStatus.Dead -> "status-dot status-dead"
        EmbedHealthStatus.Unknown -> "status-dot"
    }
    el("span", classes) {
        setAttribute("aria-hidden", "true")
    }
}

private fun healthLabel(app: App, service: EmbedService): String {
    val s = app.strings
    return when (app.statusOf(service.normalizedHost())) {
        EmbedHealthStatus.Alive -> s.embedHealthAlive
        EmbedHealthStatus.Dead -> s.embedHealthDead
        EmbedHealthStatus.Unknown -> s.embedHealthUnknown
    }
}

private fun healthLabelClasses(app: App, service: EmbedService): String =
    if (app.statusOf(service.normalizedHost()) == EmbedHealthStatus.Dead) {
        "body-small status-label-dead"
    } else {
        "body-small"
    }

/**
 * Incremental boundary for probe ticks. It deliberately looks up the current Settings nodes on
 * every call, so navigating away cannot retain or mutate detached elements.
 */
internal fun updateEmbedHealthUi(app: App): Boolean {
    val root = app.rootElement
    val summary = root.querySelector("#$EMBED_HEALTH_SUMMARY_ID") as? HTMLElement
        ?: return false
    summary.textContent = healthSummary(app)

    (root.querySelector("#$EMBED_HEALTH_REFRESH_ID") as? HTMLButtonElement)
        ?.disabled = app.healthProgress != null

    (root.querySelector("#$EMBED_HEALTH_PROGRESS_ID") as? HTMLElement)?.let { progress ->
        val state = app.healthProgress
        if (state == null) {
            progress.setAttribute("hidden", "")
            progress.removeAttribute("aria-valuemax")
            progress.removeAttribute("aria-valuenow")
        } else {
            progress.removeAttribute("hidden")
            progress.setAttribute("aria-valuemax", state.total.toString())
            progress.setAttribute("aria-valuenow", state.completedCount.toString())
        }
        (progress.querySelector(".progress-bar") as? HTMLElement)?.style?.transform =
            "scaleX(${state?.fraction ?: 0.0})"
    }

    val rows = root.querySelectorAll("[data-fixer-row]")
    for (index in 0 until rows.length) {
        val row = rows.item(index) as? HTMLElement ?: continue
        val platformKey = row.getAttribute("data-fixer-row") ?: continue
        val nextState = fixerRowHealthState(app, platformKey)
        if (row.getAttribute("data-health-state") != nextState) {
            row.clear()
            renderFixerRowContents(app, row, platformKey)
        }
    }

    val labels = root.querySelectorAll("[data-fixer-health-host]")
    for (index in 0 until labels.length) {
        val label = labels.item(index) as? HTMLElement ?: continue
        val host = label.getAttribute("data-fixer-health-host") ?: continue
        when (app.statusOf(host)) {
            EmbedHealthStatus.Alive -> {
                label.className = "body-small"
                label.textContent = app.strings.embedHealthAlive
            }
            EmbedHealthStatus.Dead -> {
                label.className = "body-small status-label-dead"
                label.textContent = app.strings.embedHealthDead
            }
            EmbedHealthStatus.Unknown -> {
                label.className = "body-small"
                label.textContent = app.strings.embedHealthUnknown
            }
        }
    }
    return true
}

private fun platformLabel(key: String): String = key.replaceFirstChar { it.uppercase() }

private fun HTMLElement.radioItem(
    label: String,
    hint: String,
    iconName: String,
    selectedLabel: String,
    group: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    el("label", if (selected) "action-option action-option-selected state" else "action-option state") {
        val input = el("input", "sr-only") {
            setAttribute("type", "radio")
            setAttribute("name", group)
        } as HTMLInputElement
        input.checked = selected
        input.onchange = { if (input.checked) onSelect(); Unit }
        icon(iconName, "icon action-option-icon")
        el("div", "list-item-text") {
            el("span", "title-small", label)
            el("span", "body-small on-surface-variant", hint)
        }
        if (selected) {
            el(
                "span",
                "chip chip-small action-selected-chip default-action-selected-chip",
                selectedLabel,
            )
        }
    }
}

private fun HTMLElement.preferenceToggle(idValue: String, title: String, hint: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    el("label", "list-item preference-toggle") {
        el("div", "list-item-text") {
            el("span", "body-large", title)
            el("span", "body-small on-surface-variant", hint)
        }
        val input = el("input", "preference-checkbox") {
            id = idValue
            setAttribute("type", "checkbox")
            setAttribute("role", "switch")
        } as HTMLInputElement
        input.checked = enabled
        input.onchange = { onChange(input.checked); Unit }
    }
}
