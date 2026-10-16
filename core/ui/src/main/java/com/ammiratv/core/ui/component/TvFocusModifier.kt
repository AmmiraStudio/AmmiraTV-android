package com.ammiratv.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ammiratv.core.ui.theme.LocalIsTv

/**
 * Universal modifier for D-pad focus and touch.
 *
 * - **Focus ring:** a bright outline is painted *on top of* everything declared after this
 *   modifier (the caller's background included) while the element has focus or is pressed. It used
 *   to be drawn underneath the caller's card background, so on a TV nothing looked selected.
 * - **TV:** the element also grows slightly when focused.
 * - **Activation:** OK / Enter on the remote, or a tap. One focus target, no double click.
 */
@Composable
fun Modifier.dpadFocusable(
    shape: Shape = RoundedCornerShape(12.dp),
    borderWidth: Dp = 3.dp,
    borderColor: Color = Color.White,
    focusedScale: Float = 1.03f,
    /** Lets the caller mirror the focus state, e.g. to start a marquee on the row's text. */
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val isTv = LocalIsTv.current
    var hasFocus by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)

    val highlighted = hasFocus || pressed
    val scale by animateFloatAsState(
        targetValue = if (isTv && hasFocus) focusedScale else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "tv_focus_scale"
    )

    return this
        // Touch targets are never smaller than 48dp
        .defaultMinSize(minHeight = 48.dp)
        .scale(scale)
        .drawWithContent {
            drawContent()
            if (highlighted) {
                val outline = shape.createOutline(size, layoutDirection, this)
                // Soft veil + hard ring: visible on dark cards and on the red selected pills alike
                drawOutline(outline, color = Color.White.copy(alpha = 0.12f))
                drawOutline(outline, color = borderColor, style = Stroke(width = borderWidth.toPx()))
            }
        }
        // Key handling sits OUTSIDE focusable(): key events bubble up from the focused node to its
        // ancestors, they are not delivered to modifiers declared after it.
        .onKeyEvent { event ->
            val isActivate = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                event.key == Key.NumPadEnter
            when {
                !isActivate -> false
                // Armed only by a KeyDown that happened HERE: the KeyUp of a press that opened
                // this screen must not click whatever got focus on it.
                event.type == KeyEventType.KeyDown -> { armed = true; true }
                event.type == KeyEventType.KeyUp -> {
                    val wasArmed = armed
                    armed = false
                    if (wasArmed) currentOnClick()
                    true
                }
                else -> false
            }
        }
        .onFocusChanged {
            hasFocus = it.hasFocus || it.isFocused
            if (!hasFocus) armed = false
            currentOnFocusChange?.invoke(hasFocus)
        }
        .focusable()
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                    }
                },
                onTap = { currentOnClick() }
            )
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            onClick { currentOnClick(); true }
        }
}
