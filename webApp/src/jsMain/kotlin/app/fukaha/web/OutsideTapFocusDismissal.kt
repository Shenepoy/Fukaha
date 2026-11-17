package app.fukaha.web

import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.MouseEvent

/** Capture completed taps, leaving scrolling, text selection and keyboard activation alone. */
internal fun installOutsideTapFocusDismissal(): () -> Unit {
    val editorSelector = "input, textarea, [contenteditable]:not([contenteditable='false'])"
    var startedInEditor = false
    val pointerListener = EventListener { event ->
        startedInEditor = (event.target as? Element)?.closest(editorSelector) != null
    }
    val listener = EventListener { event ->
        val click = event as? MouseEvent
        val active = document.activeElement as? HTMLElement
        val target = event.target as? Element
        if (!startedInEditor && click != null && click.detail > 0 && active != null && target != null &&
            (active is HTMLInputElement || active is HTMLTextAreaElement || active.isContentEditable) &&
            !active.contains(target) &&
            target.closest(editorSelector) == null &&
            (target.closest("label") as? HTMLLabelElement)?.control != active
        ) {
            active.blur()
        }
    }
    document.addEventListener("pointerdown", pointerListener, true)
    document.addEventListener("click", listener, true)
    return {
        document.removeEventListener("pointerdown", pointerListener, true)
        document.removeEventListener("click", listener, true)
    }
}
