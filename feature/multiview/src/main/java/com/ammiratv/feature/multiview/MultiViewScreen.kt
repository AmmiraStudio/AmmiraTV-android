package com.ammiratv.feature.multiview

import com.ammiratv.core.ui.component.tvFieldNav
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.MultiViewLayout
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.DarkSurface
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

@OptIn(UnstableApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MultiViewScreen(
    onBackClick: () -> Unit,
    viewModel: MultiViewViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val layouts by viewModel.savedLayouts.collectAsState()

    BackHandler(onBack = onBackClick)

    // Never promise more simultaneous streams than the hardware decoders can run
    val device = com.ammiratv.core.ui.device.LocalDeviceProfile.current
    LaunchedEffect(device.maxVideoDecoders) { viewModel.setDeviceLimit(device.maxVideoDecoders) }

    state.message?.let { text ->
        LaunchedEffect(text) {
            kotlinx.coroutines.delay(5_000)
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp)
    ) {
        // Wraps on narrow screens so no pill is pushed off the edge
        androidx.compose.foundation.layout.FlowRow(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Multi-view", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            CategoryPill("Salva layout", false, viewModel::askLayoutName)
            CategoryPill("Layout salvati (${layouts.size})", false, viewModel::toggleLayouts)
            CategoryPill("Rimuovi riquadro", false, onClick = { viewModel.clearTile(state.focusedIndex) })
        }
        state.message?.let {
            Text(it, color = AmmiraTeal, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(8.dp))

        // 2x2 grid; tiles are focusable boxes so the D-pad moves between them naturally
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until 2) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until 2) {
                        val slot = row * 2 + col
                        Tile(
                            channel = state.tiles[slot],
                            isFocused = state.focusedIndex == slot,
                            revision = state.playerRevision,
                            viewModel = viewModel,
                            slot = slot,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────

    state.pickerSlot?.let { slot ->
        ChannelPicker(
            results = state.pickerResults,
            onSearch = viewModel::searchPicker,
            onPick = { viewModel.setTile(slot, it) },
            onDismiss = viewModel::closePicker
        )
    }

    if (state.askLayoutName) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissLayoutName,
            title = { Text("Nome del layout") },
            text = {
                OutlinedTextField(modifier = androidx.compose.ui.Modifier.tvFieldNav(),

                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("es. Serie A: 4 partite") }
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.saveLayout(name) }) { Text("Salva") } },
            dismissButton = { TextButton(onClick = viewModel::dismissLayoutName) { Text("Annulla") } }
        )
    }

    if (state.showLayouts) {
        Dialog(onDismissRequest = viewModel::toggleLayouts) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(DarkSurface, RoundedCornerShape(16.dp))
                    .padding(20.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
            ) {
                Text("Layout salvati", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (layouts.isEmpty()) {
                    Text("Nessun layout salvato", color = TextSecondary, fontSize = 14.sp)
                }
                layouts.forEach { layout ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { viewModel.loadLayout(layout) },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = TextPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .dpadFocusable(shape = RoundedCornerShape(8.dp)) { viewModel.loadLayout(layout) }
                        ) { com.ammiratv.core.ui.component.ScrollingText(layout.name, forceScroll = true) }
                        Button(
                            onClick = { viewModel.deleteLayout(layout) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A30), contentColor = TextSecondary),
                            modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp)) { viewModel.deleteLayout(layout) }
                        ) { Text("Elimina") }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Tile(
    channel: Channel?,
    isFocused: Boolean,
    revision: Int,
    viewModel: MultiViewViewModel,
    slot: Int,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .onFocusChanged { if (it.isFocused || it.hasFocus) viewModel.setFocused(slot) }
            .dpadFocusable(shape = shape, focusedScale = 1.0f) { viewModel.openPicker(slot) }
            .border(2.dp, if (isFocused) AmmiraTeal else Color(0xFF2A2A30), shape)
            .background(Color.Black, shape),
        contentAlignment = Alignment.Center
    ) {
        if (channel == null) {
            Text("+ Aggiungi canale", color = TextSecondary, fontSize = 15.sp)
        } else {
            // `revision` forces a re-bind when the underlying player was swapped
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view -> if (revision >= 0) view.player = viewModel.playerFor(slot) },
                modifier = Modifier.fillMaxSize()
            )
            com.ammiratv.core.ui.component.ScrollingText(
                text = channel.cleanName + if (isFocused) "  🔊" else "",
                focused = isFocused,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .align(Alignment.BottomStart)
                    .background(Color(0xAA000000), RoundedCornerShape(topEnd = 8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ChannelPicker(
    results: List<Channel>,
    onSearch: (String) -> Unit,
    onPick: (Channel) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(DarkSurface, RoundedCornerShape(16.dp))
                .padding(20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
        ) {
            Text("Scegli un canale", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                singleLine = true,
                label = { Text("Cerca (vuoto = preferiti)") },
                modifier = Modifier.tvFieldNav().fillMaxWidth()
            )
            if (results.isEmpty()) {
                Text("Nessun canale. Aggiungi dei preferiti o cerca per nome.", color = TextSecondary, fontSize = 13.sp)
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(280.dp)
            ) {
                items(results, key = { it.id }) { channel ->
                    com.ammiratv.core.ui.component.ScrollingText(
                        text = channel.cleanName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadFocusable(shape = RoundedCornerShape(8.dp)) { onPick(channel) }
                            .background(DarkCard, RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
