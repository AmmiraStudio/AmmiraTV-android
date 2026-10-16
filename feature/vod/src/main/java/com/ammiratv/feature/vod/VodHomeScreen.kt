package com.ammiratv.feature.vod

import com.ammiratv.core.ui.component.tvFieldNav
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ammiratv.core.ui.component.ScrollingText
import com.ammiratv.core.ui.device.LocalWidthClass
import com.ammiratv.core.ui.device.WidthClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ammiratv.core.model.VodItem
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
fun VodHomeScreen(
    onBackClick: () -> Unit,
    onOpenItem: (kind: VodKind, playlistId: Long, itemId: Long) -> Unit,
    onResume: (contentKey: String) -> Unit,
    viewModel: VodHomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val items by viewModel.visibleItems.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val progress by viewModel.progressByKey.collectAsState()

    BackHandler(onBack = onBackClick)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Film e Serie", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.width(24.dp))
            CategoryPill("Film", state.kind == VodKind.MOVIE, { viewModel.setKind(VodKind.MOVIE) })
            Spacer(modifier = Modifier.width(10.dp))
            CategoryPill("Serie TV", state.kind == VodKind.SERIES, { viewModel.setKind(VodKind.SERIES) })
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.unsupported -> CenterMessage(
                "Film e serie sono disponibili per le sorgenti Xtream Codes. Seleziona o aggiungi una sorgente Xtream."
            )
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmmiraTeal)
            }
            state.error != null -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.error.orEmpty(), color = TextSecondary, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::retry,
                    colors = ButtonDefaults.buttonColors(containerColor = AmmiraTeal, contentColor = Color(0xFFFFFFFF)),
                    modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = viewModel::retry)
                ) { Text("Riprova", fontWeight = FontWeight.Bold) }
            }
            else -> {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Cerca nel catalogo") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AmmiraTeal,
                        unfocusedBorderColor = Color(0xFF3A3A42),
                        focusedLabelColor = AmmiraTeal,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier.tvFieldNav().fillMaxWidth(0.5f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Pinned first, like "Calcio Italia" in the guide
                    if (state.italianCategoryIds.isNotEmpty()) {
                        item(key = "pinned_it") {
                            CategoryPill(
                                if (state.kind == VodKind.SERIES) "Serie TV Italiane" else "Film Italiani",
                                state.selectedCategoryId == PINNED_ITALIAN,
                                { viewModel.selectCategory(PINNED_ITALIAN) }
                            )
                        }
                    }
                    item {
                        CategoryPill("Tutte", state.selectedCategoryId == null, { viewModel.selectCategory(null) })
                    }
                    items(state.categories.filter { it.id !in state.italianCategoryIds }, key = { it.id }) { category ->
                        CategoryPill(
                            category.name,
                            state.selectedCategoryId == category.id,
                            { viewModel.selectCategory(category.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Browsing (no search, no category): Netflix-style hero + horizontal rows.
                // Searching or picking a category falls back to the plain grid below.
                if (state.query.isBlank() &&
                    (state.selectedCategoryId == null || state.selectedCategoryId == PINNED_ITALIAN)
                ) {
                    val folderCategories =
                        if (state.selectedCategoryId == PINNED_ITALIAN)
                            state.categories.filter { it.id in state.italianCategoryIds }
                        else state.categories
                    NetflixHome(
                        items = items,
                        categories = folderCategories,
                        continueWatching = continueWatching,
                        progress = progress,
                        onOpenItem = onOpenItem,
                        onResume = onResume
                    )
                    return@Column
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (continueWatching.isNotEmpty() && state.query.isBlank() && state.selectedCategoryId == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ContinueWatchingRow(continueWatching, onResume)
                        }
                    }

                    items(items, key = { "${it.kind}:${it.id}" }) { item ->
                        val key = when (item.kind) {
                            VodKind.MOVIE -> WatchProgress.movieKey(item.playlistId, item.id)
                            VodKind.SERIES -> null // series progress lives on its episodes
                        }
                        PosterCard(
                            item = item,
                            progress = key?.let { progress[it] },
                            onClick = { onOpenItem(item.kind, item.playlistId, item.id) }
                        )
                    }
                }
            }
        }
    }
}

/** One horizontal shelf of the Netflix-style home. */
private data class Shelf(val title: String, val items: List<VodItem>)

private const val SHELF_SIZE = 20
private const val MAX_CATEGORY_SHELVES = 14

@Composable
private fun NetflixHome(
    items: List<VodItem>,
    categories: List<com.ammiratv.core.model.VodCategory>,
    continueWatching: List<WatchProgress>,
    progress: Map<String, WatchProgress>,
    onOpenItem: (kind: VodKind, playlistId: Long, itemId: Long) -> Unit,
    onResume: (contentKey: String) -> Unit
) {
    // Built once per catalogue: catalogues can have tens of thousands of entries, so a single
    // pass fills every shelf instead of filtering the whole list once per category.
    val shelves = androidx.compose.runtime.remember(items, categories) {
        val recent = items.filter { it.addedAt > 0 }.sortedByDescending { it.addedAt }.take(SHELF_SIZE)
        val topRated = items
            .mapNotNull { item -> item.rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { item to it } }
            .sortedByDescending { it.second }
            .take(SHELF_SIZE)
            .map { it.first }

        val byCategory = LinkedHashMap<String, MutableList<VodItem>>()
        for (item in items) {
            val list = byCategory.getOrPut(item.categoryId) { ArrayList() }
            if (list.size < SHELF_SIZE) list.add(item)
        }

        buildList {
            if (recent.isNotEmpty()) add(Shelf("Aggiunti di recente", recent))
            if (topRated.isNotEmpty()) add(Shelf("I più votati", topRated))
            categories.asSequence()
                .mapNotNull { c -> byCategory[c.id]?.takeIf { it.isNotEmpty() }?.let { Shelf(c.name, it) } }
                .take(MAX_CATEGORY_SHELVES)
                .forEach(::add)
        }
    }
    val hero = androidx.compose.runtime.remember(shelves) {
        shelves.firstOrNull()?.items?.firstOrNull { !it.posterUrl.isNullOrBlank() }
            ?: items.firstOrNull { !it.posterUrl.isNullOrBlank() }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        hero?.let { item ->
            item(key = "hero") {
                HeroBanner(item = item, onClick = { onOpenItem(item.kind, item.playlistId, item.id) })
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "continue") { ContinueWatchingRow(continueWatching, onResume) }
        }

        items(shelves, key = { it.title }) { shelf ->
            Column {
                Text(shelf.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shelf.items, key = { "${it.kind}:${it.id}" }) { item ->
                        val key = if (item.kind == VodKind.MOVIE) WatchProgress.movieKey(item.playlistId, item.id) else null
                        Box(Modifier.width(132.dp)) {
                            PosterCard(
                                item = item,
                                progress = key?.let { progress[it] },
                                onClick = { onOpenItem(item.kind, item.playlistId, item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Large featured title with a poster and a "Dettagli" button, like a streaming-service billboard. */
@Composable
private fun HeroBanner(item: VodItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xFF24121A), Color(0xFF2A0F16), Color(0xFF0A0A0C))
                )
            )
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (item.kind == VodKind.SERIES) "SERIE TV IN EVIDENZA" else "FILM IN EVIDENZA",
                color = AmmiraTeal, fontSize = 12.sp, fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(6.dp))
            ScrollingText(
                item.name, forceScroll = true, color = TextPrimary,
                fontSize = if (LocalWidthClass.current == WidthClass.COMPACT) 22.sp else 30.sp,
                fontWeight = FontWeight.Black
            )
            val meta = listOfNotNull(item.year?.takeIf { it.isNotBlank() }, item.rating?.takeIf { it.isNotBlank() }?.let { "★ $it" })
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(meta.joinToString("  •  "), color = TextSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = AmmiraTeal, contentColor = Color(0xFFFFFFFF)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(10.dp), onClick = onClick)
            ) { Text("Guarda / Dettagli", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF26262C))
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, fontSize = 16.sp)
    }
}

@Composable
private fun ContinueWatchingRow(items: List<WatchProgress>, onResume: (String) -> Unit) {
    Column {
        Text("Continua a guardare", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.contentKey }) { p ->
                val shape = RoundedCornerShape(10.dp)
                var cardFocused by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .dpadFocusable(shape = shape, focusedScale = 1.04f, onFocusChange = { cardFocused = it }) { onResume(p.contentKey) }
                        .background(DarkCard, shape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                            .background(Color(0xFF26262C))
                    ) {
                        if (!p.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = p.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { p.fraction },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = AmmiraTeal,
                        trackColor = Color(0xFF30303A)
                    )
                    Column(Modifier.padding(10.dp)) {
                        ScrollingText(p.title, focused = cardFocused, color = TextPrimary, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold)
                        p.subtitle?.let {
                            ScrollingText(it, focused = cardFocused, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PosterCard(item: VodItem, progress: WatchProgress?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    var cardFocused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(shape = shape, focusedScale = 1.05f, onFocusChange = { cardFocused = it }, onClick = onClick)
            .background(DarkCard, shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(Color(0xFF26262C)),
            contentAlignment = Alignment.Center
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(item.name.take(2).uppercase(), color = TextSecondary, fontSize = 28.sp)
            }

            if (progress?.isWatched == true) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Visto",
                    tint = AmmiraTeal,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                )
            }
        }
        if (progress != null && !progress.isWatched && progress.fraction > 0f) {
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = AmmiraTeal,
                trackColor = Color(0xFF30303A)
            )
        }
        ScrollingText(
            text = item.name,
            focused = cardFocused,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(8.dp)
        )
    }
}


