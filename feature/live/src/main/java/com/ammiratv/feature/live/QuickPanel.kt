package com.ammiratv.feature.live

import com.ammiratv.core.ui.component.tvFieldNav
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ammiratv.core.ui.component.ScrollingText
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.ChannelEpgInfo
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import com.ammiratv.domain.usecase.UniversalSearchResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Modifier.androidxFocus(requester: androidx.compose.ui.focus.FocusRequester): Modifier =
    this.then(Modifier.focusRequester(requester))

/**
 * Panel shown over the (still playing) video when OK is pressed:
 * search bar on top, current programme with progress, chips to switch source.
 */
@Composable
fun QuickPanel(
    channel: Channel,
    epgInfo: ChannelEpgInfo?,
    playlists: List<Playlist>,
    activePlaylistId: Long,
    query: String,
    results: UniversalSearchResult,
    onQueryChange: (String) -> Unit,
    onSwitchPlaylist: (Long) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onShowControls: () -> Unit,
    sleepTimerMinutes: Int? = null,
    onCycleSleepTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // The panel takes the D-pad as soon as it opens (otherwise focus stays on the video and the
    // arrows have nowhere to go): first stop is the "Controlli" chip.
    val firstFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFA0B0B0D), Color(0xE60E0E10), Color.Transparent)))
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Cerca canali e programmi") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AmmiraTeal,
                unfocusedBorderColor = Color(0xFF3A3A42),
                focusedLabelColor = AmmiraTeal,
                unfocusedLabelColor = TextSecondary
            ),
            modifier = Modifier.tvFieldNav().fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (query.trim().length >= 2) {
            SearchResultsRow(results = results, query = query, onPlayChannel = onPlayChannel)
        } else {
            CurrentProgramCard(channel = channel, epgInfo = epgInfo)
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                CategoryPill(
                    name = "Controlli",
                    isSelected = false,
                    onClick = onShowControls,
                    modifier = Modifier.androidxFocus(firstFocus)
                )
            }
            item {
                CategoryPill(
                    name = sleepTimerMinutes?.let { "Spegni tra $it min" } ?: "Timer sonno",
                    isSelected = sleepTimerMinutes != null,
                    onClick = onCycleSleepTimer
                )
            }
            // Active source first: it is the one the user most likely wants to see highlighted
            items(playlists.sortedByDescending { it.id == activePlaylistId }, key = { it.id }) { playlist ->
                CategoryPill(
                    name = playlist.name,
                    isSelected = playlist.id == activePlaylistId,
                    onClick = { onSwitchPlaylist(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun CurrentProgramCard(channel: Channel, epgInfo: ChannelEpgInfo?) {
    val current = epgInfo?.currentProgram
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(14.dp)
    ) {
        Text(
            text = channel.cleanName,
            color = AmmiraTeal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (current != null) {
            // Shows what is on air right now, so it scrolls on its own when too long
            ScrollingText(
                text = current.title,
                forceScroll = true,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatTime(current.startTime)} - ${formatTime(current.endTime)}",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { epgInfo.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AmmiraTeal,
                trackColor = Color(0xFF30303A)
            )
            epgInfo.nextProgram?.let { next ->
                Spacer(modifier = Modifier.height(6.dp))
                ScrollingText(
                    text = "A seguire (${formatTime(next.startTime)}): ${next.title}",
                    forceScroll = true,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            Text(text = "Nessuna informazione sul programma", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SearchResultsRow(
    results: UniversalSearchResult,
    query: String,
    onPlayChannel: (Channel) -> Unit
) {
    when {
        results.query != query.trim() -> Text("Ricerca in corso…", color = TextSecondary, fontSize = 14.sp)
        results.isEmpty -> Text("Nessun risultato", color = TextSecondary, fontSize = 14.sp)
        else -> {
            // Channels first, then programmes that map to a channel; the panel stays one row high.
            val entries = results.channels.take(8).map { it to it.cleanName } +
                results.programs.mapNotNull { m -> m.channel?.let { it to m.program.title } }.take(8)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries.size) { index ->
                    val (channel, label) = entries[index]
                    val shape = RoundedCornerShape(10.dp)
                    var cardFocused by remember(index) { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .width(190.dp)
                            .dpadFocusable(shape = shape, focusedScale = 1.04f, onFocusChange = { cardFocused = it }) { onPlayChannel(channel) }
                            .background(DarkCard, shape)
                            .padding(12.dp)
                    ) {
                        ScrollingText(
                            text = label,
                            focused = cardFocused,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        ScrollingText(
                            text = channel.cleanName,
                            focused = cardFocused,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

