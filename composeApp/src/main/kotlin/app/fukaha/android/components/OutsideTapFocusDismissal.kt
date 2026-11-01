package app.fukaha.android.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

private class EditableField {
    var coordinates: LayoutCoordinates? = null
    var focused = false

    fun contains(position: Offset): Boolean =
        coordinates?.takeIf { it.isAttached }?.boundsInRoot()?.contains(position) == true
}

private val LocalEditableFields = staticCompositionLocalOf<MutableSet<EditableField>?> { null }

/** Observe taps without consuming them, so the same tap still activates buttons. */
@Composable
fun OutsideTapFocusDismissal(content: @Composable () -> Unit) {
    val fields = remember { mutableSetOf<EditableField>() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val root = remember { arrayOfNulls<LayoutCoordinates>(1) }
    CompositionLocalProvider(LocalEditableFields provides fields) {
        Box(
            propagateMinConstraints = true,
            modifier = Modifier
                .onGloballyPositioned { root[0] = it }
                .pointerInput(focusManager, keyboard) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val position = root[0]?.takeIf { it.isAttached }?.localToRoot(down.position)
                        val field = fields.firstOrNull { it.focused }
                        var isOutsideTap = position != null && field != null && fields.none { it.contains(position) }
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (event.changes.size != 1 || pointer == null ||
                                (pointer.position - down.position).getDistance() > viewConfiguration.touchSlop
                            ) {
                                isOutsideTap = false
                            }
                            if (pointer != null && !pointer.pressed && isOutsideTap &&
                                field?.focused == true && field in fields
                            ) {
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) { content() }
    }
}

/** Register the field bounds, including its editor and inline controls. */
fun Modifier.dismissFocusOnOutsideTap(): Modifier = composed {
    val fields = LocalEditableFields.current
    val field = remember { EditableField() }
    DisposableEffect(fields) {
        fields?.add(field)
        onDispose { fields?.remove(field) }
    }
    this.onGloballyPositioned { field.coordinates = it }
        .onFocusChanged { field.focused = it.hasFocus }
}
