package com.ammiratv.feature.settings

import com.ammiratv.core.ui.component.tvFieldNav
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.model.Channel
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.DarkSurface
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NumberingScreen(
    onBackClick: () -> Unit,
    viewModel: NumberingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val enabled by viewModel.enabled.collectAsState()
    val unofficial by viewModel.unofficial.collectAsState()
    val sortByNumber by viewModel.sortByNumber.collectAsState()
    val timeoutMs by viewModel.timeoutMs.collectAsState()
    val remoteUrl by viewModel.remoteUrl.collectAsState()
    var editing by remember { mutableStateOf<Channel?>(null) }
    var urlDraft by remember(remoteUrl) { mutableStateOf(remoteUrl) }

    BackHandler(onBack = onBackClick)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Numerazione canali", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(
            "Digita sul telecomando il numero reale del canale (Rai 1 = 1, Sky Sport 24 = 200). " +
                "Vale solo per i canali TV italiani in diretta.",
            color = TextSecondary, fontSize = 13.sp
        )

        state.message?.let { Text(it, color = AmmiraTeal, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        if (state.isBusy) Text("Elaborazione in corso…", color = TextSecondary, fontSize = 13.sp)

        NumberingSection("Numerazione stile TV", "Mostra i numeri e permette di digitarli col telecomando") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attiva", enabled, { viewModel.setEnabled(true) })
                CategoryPill("Disattiva", !enabled, { viewModel.setEnabled(false) })
            }
        }

        NumberingSection("Ordina per numero", "Lista e zapping su/giù seguono l'ordine dei numeri (prima i canali numerati)") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attivo", sortByNumber, { viewModel.setSortByNumber(true) })
                CategoryPill("Disattivo", !sortByNumber, { viewModel.setSortByNumber(false) })
            }
        }

        NumberingSection(
            "Numeri non ufficiali (da 1000)",
            "Canali italiani non presenti nel database ricevono un numero da 1000 in ordine alfabetico. " +
                "Non sono numeri reali della TV: per questo sono spenti di default."
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Attivi", unofficial, { viewModel.setUnofficial(true) })
                CategoryPill("Spenti", !unofficial, { viewModel.setUnofficial(false) })
            }
        }

        NumberingSection("Attesa dopo l'ultima cifra", "OK conferma subito, senza aspettare") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1 s" to 1000, "1,8 s" to 1800, "3 s" to 3000).forEach { (label, ms) ->
                    CategoryPill(label, timeoutMs == ms, { viewModel.setTimeoutMs(ms) })
                }
            }
        }

        NumberingSection("Ricalcolo", state.numberedCount?.let { "$it canali numerati" } ?: "Rilegge la playlist e riassegna i numeri") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Ricalcola numerazione", false, { viewModel.recalculateNow() })
                CategoryPill(
                    if (state.showToVerify) "Nascondi da verificare" else "Canali da verificare",
                    state.showToVerify,
                    { viewModel.toggleToVerify() }
                )
            }
        }

        if (state.showToVerify) {
            NumberingSection(
                "Da verificare (${state.toVerify.size})",
                "Canali italiani che il database non ha riconosciuto: assegna un numero a mano se vuoi"
            ) {
                if (state.toVerify.isEmpty()) {
                    Text("Nessun canale da verificare", color = TextSecondary, fontSize = 13.sp)
                }
                state.toVerify.forEach { ChannelRow(it, onEdit = { c -> editing = c }) }
            }
        }

        NumberingSection("Cambia un numero a mano", "Cerca un canale; le tue scelte hanno sempre la precedenza sul database") {
            com.ammiratv.core.ui.component.TvTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = "Nome del canale"
            )
            state.results.forEach { ChannelRow(it, onEdit = { c -> editing = c }) }
        }

        NumberingSection(
            "Aggiornamento del database",
            (state.info?.let { "Versione ${it.version} • ${it.channelCount} canali" } ?: "Database incluso nell'app") +
                ". Lascia vuoto per non aggiornare da internet."
        ) {
            com.ammiratv.core.ui.component.TvTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                label = "Indirizzo https:// del file JSON",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryPill("Salva indirizzo", false, { viewModel.setRemoteUrl(urlDraft) })
                CategoryPill("Aggiorna ora", false, {
                    viewModel.setRemoteUrl(urlDraft)
                    viewModel.updateDatabase()
                })
            }
        }
    }

    editing?.let { channel ->
        EditNumberDialog(
            channel = channel,
            onDismiss = { editing = null },
            onSave = { number ->
                viewModel.setManual(channel, number)
                editing = null
            },
            onRestore = {
                viewModel.setManual(channel, null)
                editing = null
            }
        )
    }
}

@Composable
private fun ChannelRow(channel: Channel, onEdit: (Channel) -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(shape = shape, onClick = { onEdit(channel) })
            .background(DarkCard, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = channel.channelNumber?.toString() ?: "—",
            color = if (channel.channelNumber != null) AmmiraTeal else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.width(60.dp)
        )
        com.ammiratv.core.ui.component.ScrollingText(
            text = channel.cleanName,
            color = TextPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Text("Modifica", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun EditNumberDialog(
    channel: Channel,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onRestore: () -> Unit
) {
    var text by remember { mutableStateOf(channel.channelNumber?.toString().orEmpty()) }
    val number = text.toIntOrNull()?.takeIf { it in 1..9999 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(channel.cleanName) },
        text = {
            Column {
                Text("Numero da usare per questo canale (1–9999)", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(modifier = androidx.compose.ui.Modifier.tvFieldNav(),

                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { number?.let(onSave) }, enabled = number != null) { Text("Salva") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRestore) { Text("Ripristina originale") }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        }
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AmmiraTeal,
    unfocusedBorderColor = Color(0xFF3A3A42),
    focusedLabelColor = AmmiraTeal,
    unfocusedLabelColor = TextSecondary
)

@Composable
private fun NumberingSection(title: String, description: String, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .background(DarkSurface, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(description, color = TextSecondary, fontSize = 12.sp)
        content()
    }
}
