package com.ammiratv.feature.search

import com.ammiratv.core.ui.component.tvFieldNav
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.model.Channel
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import com.ammiratv.domain.usecase.ProgramAction
import com.ammiratv.domain.usecase.ProgramActions
import com.ammiratv.domain.usecase.TonightItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TonightScreen(
    onBackClick: () -> Unit,
    onWatchLive: (Channel) -> Unit,
    viewModel: TonightViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val keywords by viewModel.keywords.collectAsState()
    var newKeyword by remember { mutableStateOf("") }

    BackHandler(onBack = onBackClick)

    state.message?.let { text ->
        LaunchedEffect(text) {
            kotlinx.coroutines.delay(4_000)
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text("Cosa guardo stasera?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(
            "Suggerimenti dai tuoi canali preferiti e dalle parole chiave che segui.",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(12.dp))

        // ── Keyword rules ────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = newKeyword,
                onValueChange = { newKeyword = it },
                label = { Text("Segui: squadra, sport, attore…") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AmmiraTeal,
                    unfocusedBorderColor = Color(0xFF3A3A42),
                    focusedLabelColor = AmmiraTeal,
                    unfocusedLabelColor = TextSecondary
                ),
                modifier = Modifier.tvFieldNav().width(360.dp)
            )
            Button(
                onClick = {
                    viewModel.addKeyword(newKeyword)
                    newKeyword = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = AmmiraTeal, contentColor = Color(0xFFFFFFFF)),
                modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp)) {
                    viewModel.addKeyword(newKeyword)
                    newKeyword = ""
                }
            ) { Text("Aggiungi", fontWeight = FontWeight.Bold) }
        }

        if (keywords.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keywords.sorted().forEach { keyword ->
                    // Selecting a chip removes it: the "✕" tells the user so
                    CategoryPill("$keyword  ✕", true, { viewModel.removeKeyword(keyword) })
                }
            }
        }

        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = AmmiraTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(14.dp))

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmmiraTeal)
            }
            state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nessun suggerimento. Aggiungi canali ai preferiti, scarica la guida TV o segui una parola chiave.",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.items, key = { i, it -> "${it.recommendation.program.channelTvgId}:${it.recommendation.program.startTime}:$i" }) { _, item ->
                    TonightRow(item = item, onClick = { viewModel.activate(item, onWatchLive) })
                }
            }
        }
    }
}

@Composable
private fun TonightRow(item: TonightItem, onClick: () -> Unit) {
    val program = item.recommendation.program
    val fmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
    val action = ProgramActions.forProgram(program, System.currentTimeMillis())
    val shape = RoundedCornerShape(12.dp)
    var rowFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(shape = shape, focusedScale = 1.02f, onFocusChange = { rowFocused = it }, onClick = onClick)
            .background(DarkCard, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            com.ammiratv.core.ui.component.ScrollingText(program.title, focused = rowFocused, color = TextPrimary,
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
            com.ammiratv.core.ui.component.ScrollingText(
                "${fmt.format(Date(program.startTime))}  •  ${item.channel?.cleanName ?: "canale non in playlist"}",
                focused = rowFocused, color = TextSecondary, fontSize = 12.sp
            )
            Text(item.recommendation.reasons.joinToString("  •  "), color = AmmiraTeal, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (action == ProgramAction.WATCH_LIVE) "Guarda" else "Registra",
            color = Color(0xFFFFFFFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(AmmiraTeal, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
