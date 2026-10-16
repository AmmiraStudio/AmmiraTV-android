package com.ammiratv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.model.NetworkVerdictKind
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.domain.usecase.ChannelTestProgress
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkSurface
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.StatusOffline
import com.ammiratv.core.ui.theme.StatusOnline
import com.ammiratv.core.ui.theme.StatusWarning
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    onBackClick: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val player by viewModel.player.collectAsState()
    BackHandler(onBack = onBackClick)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Diagnostica", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        // ── Verdict ──────────────────────────────────────────────────────
        val (verdictText, verdictColor) = when (state.verdict) {
            NetworkVerdictKind.YOUR_CONNECTION ->
                "La tua connessione è al limite: la banda stimata è troppo vicina al bitrate del canale. Prova un buffer più alto o un canale a qualità inferiore." to StatusWarning
            NetworkVerdictKind.PROVIDER ->
                "La tua linea è veloce ma lo stream ha avuto problemi: il provider è instabile in questo momento." to StatusOffline
            NetworkVerdictKind.ALL_GOOD ->
                "Tutto regolare: connessione e provider funzionano bene." to StatusOnline
            NetworkVerdictKind.NOT_ENOUGH_DATA ->
                "Guarda un canale per qualche istante: servono dati dal player per dare un verdetto." to TextSecondary
        }
        Card("È colpa tua o del provider?") {
            Text(verdictText, color = verdictColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        // ── Live numbers ─────────────────────────────────────────────────
        Card("Stream attuale") {
            Stat("Banda stimata", if (player.bandwidthBps > 0) mbps(player.bandwidthBps) else "—")
            Stat("Bitrate video", if (player.videoBitrateBps > 0) mbps(player.videoBitrateBps) else "—")
            Stat("Frame persi", player.droppedFrames.toString())
        }

        // ── Data usage ───────────────────────────────────────────────────
        val dataToday by viewModel.dataToday.collectAsState()
        val dataMonth by viewModel.dataMonth.collectAsState()
        Card("Consumo dati (stream riprodotti)") {
            Stat("Oggi", megabytes(dataToday))
            Stat("Questo mese", megabytes(dataMonth))
            Text(
                "Conta solo i dati scaricati dal player; non include guida EPG e liste.",
                color = TextSecondary, fontSize = 12.sp
            )
        }

        // ── Channel test (Smart Channel Cleaner) ─────────────────────────
        val test by viewModel.testProgress.collectAsState()
        Card("Test dei canali duplicati") {
            Text(
                "Controlla quali copie dei canali funzionano davvero (un flusso alla volta, pochi byte ciascuno) " +
                    "e ordina i duplicati per affidabilità.",
                color = TextSecondary, fontSize = 13.sp
            )
            Text(
                text = when (val t = test) {
                    null -> "Non ancora eseguito in questa sessione"
                    is ChannelTestProgress.Running -> "Test in corso: ${t.tested}/${t.total}  •  funzionanti ${t.alive}"
                    is ChannelTestProgress.Finished -> "Completato: ${t.alive} funzionanti su ${t.tested}"
                    is ChannelTestProgress.Refused -> t.reason
                },
                color = if (test is ChannelTestProgress.Refused) StatusWarning else AmmiraTeal,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            val running = test is ChannelTestProgress.Running
            androidx.compose.material3.Button(
                onClick = { if (running) viewModel.cancelChannelTest() else viewModel.startChannelTest() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = AmmiraTeal, contentColor = Color(0xFFFFFFFF)
                ),
                modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp)) {
                    if (running) viewModel.cancelChannelTest() else viewModel.startChannelTest()
                }
            ) { Text(if (running) "Interrompi" else "Avvia test", fontWeight = FontWeight.Bold) }
        }

        // ── Provider report card ─────────────────────────────────────────
        val report = state.report
        Card("Pagella del provider (ultimi 7 giorni)") {
            if (report == null || !report.hasData) {
                Text("Ancora nessun dato: la pagella si compila man mano che guardi i canali.",
                    color = TextSecondary, fontSize = 14.sp)
            } else {
                Stat("Avvii riusciti", "${(report.startSuccessRate * 100).toInt()}%  su ${report.sessions}")
                Stat("Tempo medio di avvio", "${report.averageStartMs} ms")
                Stat("Interruzioni (buffering)", report.rebuffers.toString())
                Stat("Immagini bloccate", report.stalls.toString())
                Stat(
                    "Orari peggiori",
                    if (report.worstHours.isEmpty()) "nessuno"
                    else report.worstHours.joinToString(", ") { String.format(Locale.US, "%02d:00", it) }
                )
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Text(title, color = AmmiraTeal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun megabytes(bytes: Long): String =
    if (bytes >= 1_000_000_000L) String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
    else String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)

private fun mbps(bps: Long):String = String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
