package com.ammiratv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.model.Recording
import com.ammiratv.core.model.RecordingStatus
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.StatusOffline
import com.ammiratv.core.ui.theme.StatusOnline
import com.ammiratv.core.ui.theme.StatusWarning
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingsScreen(
    onBackClick: () -> Unit,
    onPlay: (contentKey: String) -> Unit,
    viewModel: RecordingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val recordings by viewModel.recordings.collectAsState()
    BackHandler(onBack = onBackClick)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text("Registrazioni", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Le registrazioni usano una connessione in più del tuo abbonamento.",
            color = TextSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))

        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nessuna registrazione. Premi OK su un canale e scegli \"Registra\", oppure dalla Guida TV.",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recordings, key = { it.id }) { rec ->
                    RecordingRow(
                        recording = rec,
                        onPlay = { viewModel.play(rec, onPlay) },
                        onStop = { viewModel.stop(rec.id) },
                        onDelete = { viewModel.delete(rec.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
    val (statusText, statusColor) = when (recording.status) {
        RecordingStatus.SCHEDULED -> "Programmata" to StatusWarning
        RecordingStatus.RECORDING -> "In registrazione" to StatusOffline
        RecordingStatus.DONE -> "Completata" to StatusOnline
        RecordingStatus.FAILED -> "Non riuscita" to StatusOffline
        RecordingStatus.CANCELLED -> "Annullata" to TextSecondary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            com.ammiratv.core.ui.component.ScrollingText(recording.title, color = TextPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            com.ammiratv.core.ui.component.ScrollingText(
                "${recording.channelName}  •  ${fmt.format(Date(recording.startTime))}" +
                    if (recording.sizeBytes > 0) "  •  ${formatSize(recording.sizeBytes)}" else "",
                color = TextSecondary, fontSize = 12.sp
            )
            Text(
                statusText + (recording.errorMessage?.let { " — $it" } ?: ""),
                color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recording.filePath != null && recording.sizeBytes > 0 && !recording.isActive) {
                SmallButton("Guarda", primary = true, onClick = onPlay)
            }
            if (recording.isActive) {
                SmallButton("Ferma", primary = false, onClick = onStop)
            } else {
                SmallButton("Elimina", primary = false, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) AmmiraTeal else Color(0xFF2A2A30),
            contentColor = if (primary) Color(0xFFFFFFFF) else TextPrimary
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.0f MB", bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}
