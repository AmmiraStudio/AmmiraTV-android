package com.ammiratv.feature.epg

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.ammiratv.core.ui.R as UiR
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.domain.usecase.ProgramAction
import com.ammiratv.domain.usecase.ProgramActions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ammiratv.core.ui.component.ExpandableText
import com.ammiratv.core.ui.component.ScrollingText
import com.ammiratv.core.ui.device.LocalWidthClass
import com.ammiratv.core.ui.device.WidthClass
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.DarkSurfaceVariant
import com.ammiratv.core.ui.theme.AmmiraRuby
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EpgGridScreen(
    onBackClick: () -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onPlayCatchup: (contentKey: String) -> Unit = {},
    viewModel: EpgGridViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val programsByChannel by viewModel.programsByChannel.collectAsState()
    val message by viewModel.message.collectAsState()
    val pinnedCategory by viewModel.pinnedCategory.collectAsState()
    val pinnedMemberIds by viewModel.pinnedMemberIds.collectAsState()
    val numberingEnabled by viewModel.numberingEnabled.collectAsState()
    val calendarContext = androidx.compose.ui.platform.LocalContext.current

    val horizontalScrollState = rememberScrollState()

    // 30-minute intervals for the timeline header
    val timeSlots = generateTimeSlots(uiState.timeWindowStart, uiState.timeWindowEnd)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 8.dp)
        ) {
            // Top Nav & Controls Row: wraps on narrow screens instead of squeezing the buttons
            FlowRow(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(38.dp)
                            .dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onBackClick)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Indietro",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Image(
                        painter = painterResource(id = UiR.drawable.app_logo),
                        contentDescription = "AmmiraTv Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Guida TV (EPG)",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                // Time Navigation Controls (-2h, Adesso, +2h)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeNavButton(
                        text = "-2h",
                        icon = Icons.Default.FastRewind,
                        onClick = { viewModel.shiftTimeWindow(-2) }
                    )
                    TimeNavButton(
                        text = "Adesso",
                        icon = null,
                        onClick = { viewModel.resetToNow() }
                    )
                    TimeNavButton(
                        text = "+2h",
                        icon = Icons.Default.FastForward,
                        onClick = { viewModel.shiftTimeWindow(2) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Focused Program Synopsis Header Card
            FocusedProgramBanner(
                program = uiState.selectedProgram,
                channel = uiState.focusedChannel,
                onPlayChannel = {
                    val channel = uiState.focusedChannel
                    val program = uiState.selectedProgram
                    when {
                        channel != null && program != null ->
                            viewModel.activateProgram(program, channel, onPlayChannel, onPlayCatchup)
                        channel != null -> onPlayChannel(channel)
                    }
                },
                onRemind = {
                    val channel = uiState.focusedChannel
                    val program = uiState.selectedProgram
                    if (channel != null && program != null) viewModel.remindMe(program, channel)
                },
                onAddToCalendar = {
                    val program = uiState.selectedProgram
                    if (program != null) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                            data = android.provider.CalendarContract.Events.CONTENT_URI
                            putExtra(android.provider.CalendarContract.Events.TITLE, program.title)
                            putExtra(
                                android.provider.CalendarContract.Events.EVENT_LOCATION,
                                uiState.focusedChannel?.cleanName.orEmpty()
                            )
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, program.startTime)
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, program.endTime)
                        }
                        // TVs usually have no calendar app: say so instead of crashing
                        try {
                            calendarContext.startActivity(intent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            viewModel.showMessage("Nessuna app Calendario su questo dispositivo")
                        }
                    }
                }
            )

            message?.let { text ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = text, color = AmmiraTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                androidx.compose.runtime.LaunchedEffect(text) {
                    kotlinx.coroutines.delay(4_000)
                    viewModel.clearMessage()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Categories Filter Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // "Calcio Italia" always comes first (the provider's EU | ITALY SPORTS group)
                pinnedCategory?.let { pinned ->
                    item(key = "pinned") {
                        CategoryPill(
                            name = "Calcio Italia",
                            isSelected = uiState.selectedCategory?.id == pinned.id,
                            onClick = { viewModel.selectCategory(pinned) }
                        )
                    }
                }
                item {
                    CategoryPill(
                        name = "Tutti i canali",
                        isSelected = uiState.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) }
                    )
                }
                items(categories.filter { it.id !in pinnedMemberIds }) { category ->
                    CategoryPill(
                        name = category.name,
                        isSelected = uiState.selectedCategory?.id == category.id,
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // EPG Grid Container
            if (channels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun canale disponibile in questa categoria",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Timeline Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .padding(vertical = 8.dp)
                    ) {
                        // Left Channel Header placeholder
                        Box(
                            modifier = Modifier
                                .width(if (LocalWidthClass.current == WidthClass.COMPACT) 120.dp else 180.dp)
                                .padding(start = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "CANALE",
                                color = AmmiraTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Scrollable Time Intervals
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            timeSlots.forEach { slotMillis ->
                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = formatTime(slotMillis),
                                        color = Color(0xFFC4C4CA),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Channels and Programs List (Vertically scrollable)
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(listState) {
                        androidx.compose.runtime.snapshotFlow {
                            val v = listState.layoutInfo.visibleItemsInfo
                            (v.firstOrNull()?.index ?: 0) to (v.lastOrNull()?.index ?: 0)
                        }.collect { (first, last) -> viewModel.onVisibleRows(first, last) }
                    }
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp)
                    ) {
                        itemsIndexed(
                            items = channels,
                            key = { _, ch -> ch.id }
                        ) { index, channel ->
                            val channelPrograms = channel.tvgId?.let { programsByChannel[it] } ?: emptyList()

                            ChannelGridRow(
                                channel = channel,
                                channelNumber = if (numberingEnabled) channel.channelNumber else index + 1,
                                programs = channelPrograms,
                                timeWindowStart = uiState.timeWindowStart,
                                timeWindowEnd = uiState.timeWindowEnd,
                                scrollState = horizontalScrollState,
                                onChannelClick = { onPlayChannel(channel) },
                                onProgramClick = { prog ->
                                    viewModel.activateProgram(prog, channel, onPlayChannel, onPlayCatchup)
                                },
                                onChannelFocus = { viewModel.onChannelFocused(channel) },
                                onProgramFocus = { prog -> viewModel.onProgramFocused(prog, channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeNavButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = DarkCard,
            contentColor = TextPrimary
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier
            .height(34.dp)
            .dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = AmmiraTeal, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FocusedProgramBanner(
    program: EpgProgram?,
    channel: Channel?,
    onPlayChannel: () -> Unit,
    onRemind: () -> Unit,
    onAddToCalendar: () -> Unit
) {
    val compact = LocalWidthClass.current == WidthClass.COMPACT

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(14.dp)
    ) {
        if (program == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (channel != null) "${channel.cleanName} — Nessuna informazione dettagliata"
                    else "Naviga con le frecce della guida per visualizzare trama e orari dei programmi",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            return@Box
        }

        val info: @Composable (Modifier) -> Unit = { mod ->
            Column(modifier = mod) {
                // Channel, time and category wrap instead of squeezing on narrow screens
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (channel != null) {
                        Text(text = channel.cleanName, color = AmmiraTeal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = "•", color = TextSecondary, fontSize = 14.sp)
                    }
                    Text(
                        text = "${formatTime(program.startTime)} - ${formatTime(program.endTime)}",
                        color = Color(0xFFFF7A59),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!program.category.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2A2A30), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = program.category ?: "", color = Color(0xFFB5B5BD), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // The banner shows the selected programme, so its title always scrolls
                ScrollingText(
                    text = program.title,
                    forceScroll = true,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!program.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    ExpandableText(
                        text = program.description ?: "",
                        title = program.title,
                        collapsedLines = 2,
                        fontSize = 12.sp
                    )
                }
            }
        }

        val actions: @Composable () -> Unit = {
            val isFuture = program.startTime > System.currentTimeMillis()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayChannel,
                    colors = ButtonDefaults.buttonColors(containerColor = AmmiraTeal, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onPlayChannel)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (ProgramActions.forProgram(program, System.currentTimeMillis())) {
                            ProgramAction.WATCH_CATCHUP -> "Rivedi"
                            ProgramAction.WATCH_LIVE -> "Guarda"
                            ProgramAction.RECORD -> "Registra"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                // Future programmes can also just be reminded, without recording
                if (isFuture) {
                    Button(
                        onClick = onRemind,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A30), contentColor = TextPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onRemind)
                    ) { Text(text = "Ricordami", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Button(
                        onClick = onAddToCalendar,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A30), contentColor = TextPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onAddToCalendar)
                    ) { Text(text = "Calendario", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }

        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                info(Modifier.fillMaxWidth())
                actions()
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                info(Modifier.weight(1f))
                Spacer(modifier = Modifier.width(16.dp))
                actions()
            }
        }
    }
}

@Composable
private fun ChannelGridRow(
    channel: Channel,
    channelNumber: Int?,
    programs: List<EpgProgram>,
    timeWindowStart: Long,
    timeWindowEnd: Long,
    scrollState: androidx.compose.foundation.ScrollState,
    onChannelClick: () -> Unit,
    onProgramClick: (EpgProgram) -> Unit,
    onChannelFocus: () -> Unit,
    onProgramFocus: (EpgProgram) -> Unit
) {
    var channelFocused by remember(channel.id) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        // Left Channel Box
        Box(
            modifier = Modifier
                .width(if (LocalWidthClass.current == WidthClass.COMPACT) 120.dp else 180.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkCard)
                .dpadFocusable(
                    shape = RoundedCornerShape(8.dp),
                    focusedScale = 1.02f,
                    onFocusChange = { channelFocused = it },
                    onClick = onChannelClick
                )
                .onFocusChanged { if (it.isFocused) onChannelFocus() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channelNumber?.toString().orEmpty(),
                    color = AmmiraTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.width(34.dp)
                )

                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                ScrollingText(
                    text = channel.cleanName,
                    focused = channelFocused,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Right Scrollable Programs Row
        Row(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (programs.isEmpty()) {
                // Placeholder block when no EPG is available
                Box(
                    modifier = Modifier
                        .width(600.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1C1C22))
                        .dpadFocusable(shape = RoundedCornerShape(6.dp), onClick = onChannelClick)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Nessuna guida per questo canale • Premi OK per guardare",
                        color = Color(0xFF77777F),
                        fontSize = 12.sp
                    )
                }
            } else {
                programs.forEach { program ->
                    // Calculate visual width: each 30 minutes = 160.dp (approx 5.33 dp per minute)
                    val durationMillis = (program.endTime - program.startTime).coerceAtLeast(10 * 60 * 1000L)
                    val durationMinutes = durationMillis / (60 * 1000f)
                    val blockWidth = (durationMinutes * (160f / 30f)).coerceIn(90f, 600f).dp

                    val now = System.currentTimeMillis()
                    val isOnAir = program.startTime <= now && program.endTime > now
                    var cellFocused by remember(program.startTime, program.title) { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .width(blockWidth)
                            .height(50.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isOnAir) Color(0xFF3A1A22) else Color(0xFF1C1C22)
                            )
                            .border(
                                width = if (isOnAir) 1.dp else 0.dp,
                                color = if (isOnAir) AmmiraTeal else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .dpadFocusable(
                                shape = RoundedCornerShape(6.dp),
                                focusedScale = 1.03f,
                                onFocusChange = { cellFocused = it },
                                onClick = { onProgramClick(program) }
                            )
                            .onFocusChanged { if (it.isFocused) onProgramFocus(program) }
                            // Tight vertical padding: with 6 dp the second line (the time) was
                            // clipped at the bottom of the 50 dp cell.
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(verticalArrangement = Arrangement.Center) {
                            if (isOnAir) {
                                // DAZN-style live marker
                                Box(
                                    modifier = Modifier
                                        .background(AmmiraRuby, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            ScrollingText(
                                text = program.title,
                                focused = cellFocused,
                                color = if (isOnAir) AmmiraTeal else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${formatTime(program.startTime)} - ${formatTime(program.endTime)}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun generateTimeSlots(startMillis: Long, endMillis: Long): List<Long> {
    val slots = mutableListOf<Long>()
    val halfHour = 30 * 60 * 1000L
    var current = startMillis
    while (current < endMillis) {
        slots.add(current)
        current += halfHour
    }
    return slots
}

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

