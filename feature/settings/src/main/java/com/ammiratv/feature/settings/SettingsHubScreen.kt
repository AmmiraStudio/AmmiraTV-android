package com.ammiratv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import androidx.compose.foundation.layout.Box

/** One entry of the settings hub. */
data class SettingsEntry(val title: String, val description: String, val onClick: () -> Unit)

@Composable
fun SettingsHubScreen(
    entries: List<SettingsEntry>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)
    val compact = com.ammiratv.core.ui.device.LocalWidthClass.current == com.ammiratv.core.ui.device.WidthClass.COMPACT

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 16.dp else 24.dp)
    ) {
        Text("Impostazioni", fontSize = if (compact) 24.sp else 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(20.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Phones use the whole width: no empty band on the right
            modifier = Modifier.fillMaxWidth(if (compact) 1f else 0.7f)
        ) {
            entries.forEach { entry ->
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dpadFocusable(shape = shape, focusedScale = 1.02f, onClick = entry.onClick)
                        .background(DarkCard, shape)
                        .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 14.dp else 16.dp)
                ) {
                    Column {
                        Text(entry.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(entry.description, color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
