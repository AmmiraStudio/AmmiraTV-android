package com.ammiratv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ammiratv.core.ui.component.ExpandableText
import com.ammiratv.core.ui.component.ScrollingText
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.device.LocalDeviceProfile
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

private val LONG_TITLES = listOf(
    "IT| Sky Sport Calcio Serie A Campionato Italiano - Diretta Integrale con Pre-partita, Telecronaca e Post-partita FHD HEVC",
    "Il Signore degli Anelli: Le Due Torri (Extended Edition) — Versione Restaurata 4K con Audio Italiano 5.1 e Sottotitoli",
    "Supercalifragilistichespiralidosissimo_senza_spazi_per_provare_il_taglio_di_parole_molto_molto_lunghe_ABCDEFGHIJKLMNOP",
    "📺 Canale con emoji 🎬🍿⚽ e caratteri speciali: àèéìòù ÄÖÜß ñ ç — «virgolette» e “doppie” – trattini",
    "Rai 1"
)

private val LONG_PARAGRAPH = buildString {
    repeat(14) {
        append("Una trama molto lunga che deve poter essere letta per intero, riga dopo riga, ")
        append("anche con il telecomando: scorri con su e giù, oppure con il dito. ")
    }
}

/**
 * Developer fixture: every long-text component with worst-case content, so overflow can be checked
 * by eye on each device (and at 130% / 200% system font size) without hunting for such data in
 * a real playlist.
 */
@Composable
fun LongTextFixtureScreen(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBackClick)
    val device = LocalDeviceProfile.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Test testi lunghi", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Dispositivo: ${device.formFactor}  •  touch: ${device.hasTouch}  •  poca RAM: ${device.lowRam}  •  " +
                    "decoder: ${device.maxVideoDecoders}  •  riduci animazioni: ${device.reduceMotion}",
                color = TextSecondary, fontSize = 12.sp
            )
        }
        items(LONG_TITLES.size) { index ->
            var focused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .dpadFocusable(
                        shape = RoundedCornerShape(10.dp),
                        focusedScale = 1.02f,
                        onFocusChange = { focused = it },
                        onClick = {}
                    )
                    .background(DarkCard, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                ScrollingText(
                    text = LONG_TITLES[index],
                    focused = focused,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCard, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Text("Trama (apri \"Mostra tutto\")", color = TextPrimary, fontWeight = FontWeight.Bold)
                ExpandableText(text = LONG_PARAGRAPH, title = "Trama di prova", collapsedLines = 3)
            }
        }
    }
}
