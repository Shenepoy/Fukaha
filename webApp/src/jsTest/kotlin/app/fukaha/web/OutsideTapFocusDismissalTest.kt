package app.fukaha.web

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class OutsideTapFocusDismissalTest {
    private fun fixture(test: (HTMLElement, HTMLInputElement) -> Unit) {
        val root = document.createElement("div") as HTMLElement
        root.innerHTML = """
            <label for="focus-test-input">Link</label>
            <input id="focus-test-input" value="https://example.com">
            <input id="focus-test-other">
            <button type="button">Action</button><div class="blank">Background</div>
        """.trimIndent()
        document.body!!.appendChild(root)
        val dispose = installOutsideTapFocusDismissal()
        try {
            test(root, root.querySelector("input") as HTMLInputElement)
        } finally {
            dispose()
            root.remove()
        }
    }

    private fun HTMLElement.tap(detail: Int = 1) {
        if (detail > 0) dispatchEvent(MouseEvent("pointerdown", MouseEventInit(bubbles = true)))
        dispatchEvent(MouseEvent("click", MouseEventInit(bubbles = true, detail = detail)))
    }

    @Test
    fun outsideTapBlursWithoutChangingTextOrSwallowingButtonAction() = fixture { root, input ->
        var clicks = 0
        val button = root.querySelector("button") as HTMLElement
        button.addEventListener("click", { clicks++ })
        input.focus()
        button.tap()
        assertFalse(document.activeElement === input)
        assertEquals("https://example.com", input.value)
        assertEquals(1, clicks)
        input.focus()
        (root.querySelector(".blank") as HTMLElement).tap()
        assertFalse(document.activeElement === input)
    }

    @Test
    fun fieldAndLabelTapsPreserveFocusAndSelection() = fixture { root, input ->
        input.focus()
        input.setSelectionRange(2, 7)
        input.tap()
        assertSame(input, document.activeElement)
        assertEquals(2, input.selectionStart)
        assertEquals(7, input.selectionEnd)
        (root.querySelector("label") as HTMLElement).tap()
        assertSame(input, document.activeElement)
        val other = root.querySelector("#focus-test-other") as HTMLElement
        other.focus()
        other.tap()
        assertSame(other, document.activeElement)
    }

    @Test
    fun keyboardActivationDoesNotDismissTheEditor() = fixture { root, input ->
        input.focus()
        (root.querySelector("button") as HTMLElement).tap(detail = 0)
        assertSame(input, document.activeElement)
    }

    @Test
    fun selectionDragStartingInsideTheFieldDoesNotDismissIt() = fixture { root, input ->
        input.focus()
        input.setSelectionRange(2, 7)
        input.dispatchEvent(MouseEvent("pointerdown", MouseEventInit(bubbles = true)))
        root.dispatchEvent(MouseEvent("click", MouseEventInit(bubbles = true, detail = 1)))
        assertSame(input, document.activeElement)
        assertEquals(2, input.selectionStart)
        assertEquals(7, input.selectionEnd)
    }
}
