package com.ammiratv.feature.search

import com.ammiratv.core.ui.component.tvFieldNav
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.model.Channel
import com.ammiratv.core.ui.component.ChannelItemView
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import com.ammiratv.domain.usecase.ProgramMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onOpenTonight: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BackHandler(onBack = onBackClick)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Cerca",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = { Text("Canali e programmi in onda") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AmmiraTeal,
                unfocusedBorderColor = Color(0xFF3A3A42),
                focusedLabelColor = AmmiraTeal,
                unfocusedLabelColor = TextSecondary
            ),
            modifier = Modifier.tvFieldNav()
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(10.dp))
        com.ammiratv.core.ui.component.CategoryPill(
            name = "Cosa guardo stasera?",
            isSelected = false,
            onClick = onOpenTonight
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            query.trim().length < 2 -> Hint("Scrivi almeno 2 lettere per cercare un canale o un programma.")
            // `results` lags the query by the debounce: don't flash "no results" for the old query
            results.query != query.trim() -> Hint("Ricerca in corso…")
            results.isEmpty -> Hint("Nessun risultato per \"${results.query}\"")
            else -> ResultsList(
                channels = results.channels,
                programs = results.programs,
                onPlayChannel = onPlayChannel
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(text = text, color = TextSecondary, fontSize = 16.sp)
    }
}

@Composable
private fun ResultsList(
    channels: List<Channel>,
    programs: List<ProgramMatch>,
    onPlayChannel: (Channel) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (channels.isNotEmpty()) {
            item { SectionTitle("Canali (${channels.size})") }
            itemsIndexed(items = channels, key = { _, c -> "c${c.id}" }) { index, channel ->
                ChannelItemView(
                    channel = channel,
                    channelNumber = channel.channelNumber,
                    onChannelClick = onPlayChannel
                )
            }
        }

        if (programs.isNotEmpty()) {
            item { SectionTitle("Programmi (${programs.size})") }
            itemsIndexed(items = programs, key = { i, m -> "p${m.program.id}_$i" }) { _, match ->
                ProgramRow(match = match, onPlayChannel = onPlayChannel)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AmmiraTeal,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ProgramRow(
    match: ProgramMatch,
    onPlayChannel: (Channel) -> Unit
) {
    val program = match.program
    val channel = match.channel
    val now = System.currentTimeMillis()
    val isOnAir = program.startTime <= now && program.endTime > now
    val shape = RoundedCornerShape(12.dp)
    var rowFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(shape = shape, focusedScale = 1.02f, onFocusChange = { rowFocused = it }) {
                // A programme with no matching channel is informational only
                channel?.let(onPlayChannel)
            }
            .background(DarkCard, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            com.ammiratv.core.ui.component.ScrollingText(
                text = program.title,
                focused = rowFocused,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            com.ammiratv.core.ui.component.ScrollingText(
                text = "${formatDay(program.startTime)}  ${formatTime(program.startTime)} - ${formatTime(program.endTime)}" +
                    (channel?.let { "  •  ${it.cleanName}" } ?: ""),
                focused = rowFocused,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        if (isOnAir) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "IN ONDA",
                color = Color(0xFFFFFFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(AmmiraTeal, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatDay(millis: Long): String =
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(millis))
