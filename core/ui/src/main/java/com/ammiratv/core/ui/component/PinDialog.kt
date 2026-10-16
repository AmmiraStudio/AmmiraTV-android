package com.ammiratv.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.DarkSurfaceVariant
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.StatusOffline
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

private const val PIN_LENGTH = 4

/**
 * D-pad friendly numeric keypad. Collects [PIN_LENGTH] digits then calls [onComplete];
 * the caller decides whether the PIN is right and passes [errorMessage] back to show it.
 */
@Composable
fun PinDialog(
    title: String,
    onComplete: (String) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var digits by remember { mutableStateOf("") }

    fun press(d: String) {
        if (digits.length >= PIN_LENGTH) return
        val next = digits + d
        if (next.length == PIN_LENGTH) {
            digits = ""
            onComplete(next)
        } else {
            digits = next
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceVariant)
                .padding(24.dp)
        ) {
            Text(text = title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(14.dp))

            // Filled / empty dots
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(PIN_LENGTH) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (index < digits.length) AmmiraTeal else Color(0xFF44444D))
                    )
                }
            }

            Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                if (errorMessage != null) {
                    Text(text = errorMessage, color = StatusOffline, fontSize = 13.sp)
                }
            }

            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(48.dp)
                                    .dpadFocusable(shape = shape) {
                                        when (key) {
                                            "⌫" -> digits = digits.dropLast(1)
                                            "OK" -> onDismiss()
                                            else -> press(key)
                                        }
                                    }
                                    .background(DarkCard, shape)
                            ) {
                                Text(
                                    text = if (key == "OK") "Esci" else key,
                                    color = if (key == "OK") TextSecondary else TextPrimary,
                                    fontSize = if (key == "OK") 13.sp else 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
