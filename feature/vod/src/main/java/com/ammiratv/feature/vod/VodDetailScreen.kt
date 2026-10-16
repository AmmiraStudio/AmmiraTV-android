package com.ammiratv.feature.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ammiratv.core.ui.component.ExpandableText
import com.ammiratv.core.ui.component.ScrollingText
import com.ammiratv.core.ui.device.LocalWidthClass
import com.ammiratv.core.ui.device.WidthClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ammiratv.core.model.Episode
import com.ammiratv.core.model.VodKind
import com.ammiratv.core.model.WatchProgress
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

@Composable
fun VodDetailScreen(
    onBackClick: () -> Unit,
    onPlay: (contentKey: String) -> Unit,
    viewModel: VodDetailViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progressByKey.collectAsState()

    BackHandler(onBack = onBackClick)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                color = AmmiraTeal,
                modifier = Modifier.align(Alignment.Center)
            )
            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error.orEmpty(), color = TextSecondary, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                ActionButton("Riprova", primary = true, onClick = viewModel::retry)
            }
            state.item != null -> {
                val item = state.item!!
                val details = state.details
                val isMovie = item.kind == VodKind.MOVIE
                val compact = LocalWidthClass.current == WidthClass.COMPACT

                if (compact) {
                    // Phone held upright: ONE scrolling column (poster + title on top, then the
                    // rest). The side-by-side layout below squeezes the text into a thin strip.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(104.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF26262C))
                                ) {
                                    if (!item.posterUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.posterUrl,
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(item.name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    val meta = listOfNotNull(
                                        details.releaseDate?.take(4),
                                        details.genre,
                                        details.durationLabel,
                                        details.rating?.let { "★ $it" }
                                    ).joinToString("  •  ")
                                    if (meta.isNotEmpty()) Text(meta, color = AmmiraTeal, fontSize = 13.sp)
                                }
                            }
                        }
                        item {
                            if (isMovie) {
                                MovieActions(
                                    progress = progress[WatchProgress.movieKey(item.playlistId, item.id)],
                                    onPlay = { viewModel.playMovie(onPlay) },
                                    onToggleWatched = viewModel::toggleMovieWatched
                                )
                            } else if (state.seasons.isNotEmpty()) {
                                ActionButton("Guarda il prossimo episodio", primary = true, icon = true) {
                                    viewModel.nextEpisodeToWatch()?.let { viewModel.playEpisode(it, onPlay) }
                                }
                            }
                        }
                        details.plot?.let { plot ->
                            item { ExpandableText(text = plot, title = item.name, collapsedLines = 5, fontSize = 14.sp) }
                        }
                        listOfNotNull(
                            details.director?.let { "Regia: $it" },
                            details.cast?.let { "Cast: $it" }
                        ).forEach { line ->
                            item { ExpandableText(text = line, title = item.name, collapsedLines = 2, fontSize = 12.sp) }
                        }
                        if (!isMovie) {
                            if (state.seasons.isEmpty()) {
                                item { Text("Nessun episodio disponibile", color = TextSecondary, fontSize = 14.sp) }
                            } else {
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(state.seasons.keys.toList()) { season ->
                                            CategoryPill("Stagione $season", season == state.selectedSeason, { viewModel.selectSeason(season) })
                                        }
                                    }
                                }
                                items(state.seasons[state.selectedSeason].orEmpty(), key = { it.id }) { episode ->
                                    EpisodeRow(
                                        episode = episode,
                                        watched = progress[WatchProgress.episodeKey(item.playlistId, episode.id)]?.isWatched == true,
                                        onPlay = { viewModel.playEpisode(episode, onPlay) },
                                        onToggleWatched = { viewModel.toggleEpisodeWatched(episode) }
                                    )
                                }
                            }
                        }
                    }
                } else Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // ── Poster ───────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .width(if (compact) 110.dp else 200.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF26262C))
                    ) {
                        if (!item.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // ── Info + actions + episodes ────────────────────────
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, color = TextPrimary, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Bold)

                        val meta = listOfNotNull(
                            details.releaseDate?.take(4),
                            details.genre,
                            details.durationLabel,
                            details.rating?.let { "★ $it" }
                        ).joinToString("  •  ")
                        if (meta.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(meta, color = AmmiraTeal, fontSize = 14.sp)
                        }

                        details.plot?.let {
                            Spacer(Modifier.height(10.dp))
                            ExpandableText(text = it, title = item.name, collapsedLines = 4, fontSize = 14.sp)
                        }
                        listOfNotNull(
                            details.director?.let { "Regia: $it" },
                            details.cast?.let { "Cast: $it" }
                        ).forEach {
                            Spacer(Modifier.height(4.dp))
                            ExpandableText(text = it, title = item.name, collapsedLines = 2, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(16.dp))

                        if (isMovie) {
                            MovieActions(
                                progress = progress[WatchProgress.movieKey(item.playlistId, item.id)],
                                onPlay = { viewModel.playMovie(onPlay) },
                                onToggleWatched = viewModel::toggleMovieWatched
                            )
                        } else {
                            SeriesSection(
                                seasons = state.seasons,
                                selectedSeason = state.selectedSeason,
                                playlistId = item.playlistId,
                                progress = progress,
                                onSelectSeason = viewModel::selectSeason,
                                onPlayNext = {
                                    viewModel.nextEpisodeToWatch()?.let { viewModel.playEpisode(it, onPlay) }
                                },
                                onPlayEpisode = { viewModel.playEpisode(it, onPlay) },
                                onToggleWatched = viewModel::toggleEpisodeWatched
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MovieActions(
    progress: WatchProgress?,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit
) {
    val resumeMinutes = progress?.takeIf { !it.isWatched && it.positionMs > 0 }?.positionMs?.div(60_000)
    // Wraps onto a second line on a narrow screen instead of overflowing
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionButton(
            text = if (resumeMinutes != null) "Riprendi da $resumeMinutes min" else "Riproduci",
            primary = true,
            icon = true,
            onClick = onPlay
        )
        ActionButton(
            text = if (progress?.isWatched == true) "Segna da vedere" else "Segna come visto",
            primary = false,
            onClick = onToggleWatched
        )
    }
}

@Composable
private fun SeriesSection(
    seasons: Map<Int, List<Episode>>,
    selectedSeason: Int?,
    playlistId: Long,
    progress: Map<String, WatchProgress>,
    onSelectSeason: (Int) -> Unit,
    onPlayNext: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onToggleWatched: (Episode) -> Unit
) {
    if (seasons.isEmpty()) {
        Text("Nessun episodio disponibile", color = TextSecondary, fontSize = 14.sp)
        return
    }

    ActionButton("Guarda il prossimo episodio", primary = true, icon = true, onClick = onPlayNext)
    Spacer(Modifier.height(12.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(seasons.keys.toList()) { season ->
            CategoryPill("Stagione $season", season == selectedSeason, { onSelectSeason(season) })
        }
    }

    Spacer(Modifier.height(10.dp))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(seasons[selectedSeason].orEmpty(), key = { it.id }) { episode ->
            val watched = progress[WatchProgress.episodeKey(playlistId, episode.id)]?.isWatched == true
            EpisodeRow(
                episode = episode,
                watched = watched,
                onPlay = { onPlayEpisode(episode) },
                onToggleWatched = { onToggleWatched(episode) }
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    watched: Boolean,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    var rowFocused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(shape = shape, focusedScale = 1.02f, onFocusChange = { rowFocused = it }, onClick = onPlay)
            .background(DarkCard, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text("${episode.number}", color = AmmiraTeal, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            ScrollingText(episode.title, focused = rowFocused, color = TextPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
            if (episode.durationSecs > 0) {
                Text("${episode.durationSecs / 60} min", color = TextSecondary, fontSize = 12.sp)
            }
        }
        if (watched) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Visto", tint = AmmiraTeal, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        ActionButton(if (watched) "Da vedere" else "Visto", primary = false, onClick = onToggleWatched)
    }
}

@Composable
private fun ActionButton(text: String, primary: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) AmmiraTeal else Color(0xFF2A2A30),
            contentColor = if (primary) Color(0xFFFFFFFF) else TextPrimary
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        if (icon) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

