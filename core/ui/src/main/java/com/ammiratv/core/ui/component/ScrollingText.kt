package com.ammiratv.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ammiratv.core.ui.device.LocalDeviceProfile
import com.ammiratv.core.ui.theme.DarkSurface
import com.ammiratv.core.ui.theme.FocusBorder
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * One-line text that scrolls like a sign when it does not fit, so nothing is lost to "…".
 *
 * - TV / Fire TV: scrolls only while [focused] (pass the focus state of the row/card holding it).
 * - Phone / tablet: scrolls by itself, slowly and with a pause at both ends, unless the device is
 *   low on RAM (then only when [focused] / [forceScroll]).
 * - "Remove animations" on: never animates. The text wraps onto up to [reduceMotionLines] lines
 *   instead, so it is still fully readable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    forceScroll: Boolean = false,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = TextStyle.Default,
    reduceMotionLines: Int = 3
) {
    val device = LocalDeviceProfile.current
    val auto = !device.isTv && !device.lowRam
    val animate = !device.reduceMotion && (focused || forceScroll || auto)

    when {
        device.reduceMotion -> Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            style = style,
            maxLines = reduceMotionLines,
            overflow = TextOverflow.Ellipsis
        )

        animate -> Text(
            text = text,
            modifier = modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 1200,
                repeatDelayMillis = 1500,
                velocity = 40.dp
            ),
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )

        else -> Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Multi-line text (plot, programme description, errors). Shows up to [collapsedLines] lines; when
 * the text is longer a "Mostra tutto" control opens the whole text in a scrollable panel.
 * The control is focusable, so D-pad users reach it with OK; touch users just tap.
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    collapsedLines: Int = 4,
    color: Color = TextSecondary,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    var overflows by remember(text) { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    val focus = remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            maxLines = collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { overflows = it.hasVisualOverflow }
        )
        if (overflows) {
            Text(
                text = "Mostra tutto",
                color = AmmiraTeal,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .dpadFocusable(
                        shape = RoundedCornerShape(8.dp),
                        focusedScale = 1.0f,
                        onClick = { open = true }
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }

    if (open) LongTextDialog(title = title, text = text, onDismiss = { open = false })
}

/** Full-screen reader: scrolls with finger, mouse wheel and D-pad; Back or the close row leaves it. */
@Composable
fun LongTextDialog(title: String?, text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val closeFocus = remember { FocusRequester() }
        var bodyFocused by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(DarkSurface, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .border(
                        width = if (bodyFocused) 2.dp else 0.dp,
                        color = if (bodyFocused) FocusBorder else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .onFocusChanged { bodyFocused = it.isFocused }
                    // D-pad up/down scrolls the text. At either end the key is not consumed, so focus
                    // moves on to the close button instead of getting stuck inside the text.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> if (scroll.canScrollForward) {
                                scope.launch { scroll.animateScrollBy(240f) }; true
                            } else false
                            Key.DirectionUp -> if (scroll.canScrollBackward) {
                                scope.launch { scroll.animateScrollBy(-240f) }; true
                            } else false
                            else -> false
                        }
                    }
                    .focusable()
                    .verticalScroll(scroll)
                    .padding(8.dp)
            ) {
                Text(text = text, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
            }
            Text(
                text = "Chiudi",
                color = AmmiraTeal,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier
                    .focusRequester(closeFocus)
                    .dpadFocusable(shape = RoundedCornerShape(10.dp), focusedScale = 1.0f, onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Chiudi il testo completo" }
            )
        }
    }
}
