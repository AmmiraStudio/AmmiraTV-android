package com.ammiratv.core.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ammiratv.core.ui.theme.LocalIsTv
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

/**
 * Text field that behaves for both thumbs and remotes.
 *
 * - Phone / tablet: a normal outlined field.
 * - TV / Fire TV: the field is shown as a focusable box. Moving across it with the D-pad does NOT
 *   open the on-screen keyboard (that was what made filling a form with a remote so tiring). The
 *   keyboard opens only when OK is pressed on it, and closes when the user presses Done / Back,
 *   leaving focus on the same box so the arrows continue from there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = AmmiraTeal,
        unfocusedBorderColor = Color(0xFF3A3A42),
        focusedLabelColor = AmmiraTeal,
        unfocusedLabelColor = TextSecondary
    )
    val transformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
    val options = KeyboardOptions(
        keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
        imeAction = ImeAction.Done
    )

    if (!LocalIsTv.current) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = options,
            colors = colors,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    var editing by remember { mutableStateOf(false) }
    var keyboardWasShown by remember { mutableStateOf(false) }
    val boxFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible

    // Back / hide on the keyboard ends editing and hands focus back to the box
    LaunchedEffect(imeVisible, editing) {
        if (editing && imeVisible) keyboardWasShown = true
        if (editing && keyboardWasShown && !imeVisible) editing = false
    }
    var hasEdited by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (editing) {
            hasEdited = true
            keyboardWasShown = false
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        } else if (hasEdited) {
            // Back from typing: the arrows continue from this very field. (Never on first show:
            // a screen must not have its first form field grab the focus by itself.)
            runCatching { boxFocus.requestFocus() }
        }
    }

    if (editing) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            visualTransformation = transformation,
            keyboardOptions = options,
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                editing = false
            }),
            colors = colors,
            modifier = modifier.fillMaxWidth().focusRequester(fieldFocus)
        )
    } else {
        val shape = RoundedCornerShape(4.dp)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(boxFocus)
                .dpadFocusable(shape = shape, focusedScale = 1.0f, onClick = { editing = true })
                .border(1.dp, Color(0xFF3A3A42), shape)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(label, color = TextSecondary, fontSize = 11.sp)
                val shown = when {
                    value.isEmpty() -> placeholder.orEmpty()
                    isPassword -> "•".repeat(value.length.coerceAtMost(24))
                    else -> value
                }
                ScrollingText(
                    text = shown.ifEmpty { " " },
                    forceScroll = false,
                    color = if (value.isEmpty()) TextSecondary else TextPrimary,
                    fontSize = 16.sp
                )
            }
        }
    }
}