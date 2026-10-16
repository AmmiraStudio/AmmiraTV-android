package com.ammiratv.feature.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.Category
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.core.model.VodKind
import com.ammiratv.core.model.WatchProgress
import com.ammiratv.domain.usecase.BuildCatchupUrlUseCase
import com.ammiratv.domain.usecase.GetCategoriesUseCase
import com.ammiratv.domain.usecase.GetChannelsUseCase
import com.ammiratv.domain.usecase.GetGridEpgUseCase
import com.ammiratv.domain.usecase.ObserveActiveProfileUseCase
import com.ammiratv.domain.usecase.ProgramAction
import com.ammiratv.domain.usecase.ProgramActions
import com.ammiratv.domain.usecase.RecordingOutcome
import com.ammiratv.domain.usecase.SaveWatchProgressUseCase
import com.ammiratv.domain.usecase.StartRecordingUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** Id of the virtual "Calcio Italia" folder; never collides with a database category id. */
private const val PINNED_CATEGORY_ID = -1000L

data class EpgGridUiState(
    val playlistId: Long = 0L,
    val selectedCategory: Category? = null,
    val timeWindowStart: Long = getInitialWindowStart(),
    val timeWindowEnd: Long = getInitialWindowStart() + (5 * 3600 * 1000L), // 5 hours window
    val selectedProgram: EpgProgram? = null,
    val focusedChannel: Channel? = null,
    val isLoading: Boolean = false
)

private fun getInitialWindowStart(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val minutes = cal.get(Calendar.MINUTE)
    if (minutes < 30) {
        cal.set(Calendar.MINUTE, 0)
    } else {
        cal.set(Calendar.MINUTE, 30)
    }
    // Start 30 minutes before current time slot for context
    return cal.timeInMillis - (30 * 60 * 1000L)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EpgGridViewModel @Inject constructor(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getChannelsUseCase: GetChannelsUseCase,
    private val getGridEpgUseCase: GetGridEpgUseCase,
    private val observeActiveProfile: ObserveActiveProfileUseCase,
    private val buildCatchupUrl: BuildCatchupUrlUseCase,
    private val saveProgress: SaveWatchProgressUseCase,
    private val startRecording: StartRecordingUseCase,
    private val preferences: AmmiraTvPreferences,
    private val reminderScheduler: com.ammiratv.domain.repository.ReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpgGridUiState())
    val uiState: StateFlow<EpgGridUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.activePlaylistId.collect { id ->
                _uiState.update { it.copy(playlistId = id) }
            }
        }
    }

    // Categories stream for current playlist (re-queried only when the playlist changes)
    val categories: StateFlow<List<Category>> = _uiState
        .map { it.playlistId }
        .distinctUntilChanged()
        .flatMapLatest { playlistId ->
            getCategoriesUseCase(playlistId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The provider groups that make up "Calcio Italia": every Italian football/sports section of
     * the active playlist (ITALY SPORTS, DAZN Serie A/B/Evento, ...), plain sports first. Chosen by
     * name because ids and naming differ per provider; see ItalianCategories.
     */
    private val pinnedMembers: StateFlow<List<Category>> = categories
        .map { list -> com.ammiratv.core.model.ItalianCategories.sportsSections(list) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** TV-style numbering on/off: when on, rows show the real number (blank if none) instead of the position. */
    val numberingEnabled: StateFlow<Boolean> = preferences.numberingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Ids of the groups folded into "Calcio Italia"; the category bar hides them as separate pills. */
    val pinnedMemberIds: StateFlow<Set<Long>> = pinnedMembers
        .map { members -> members.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * The pinned folder shown before everything else in the guide. It is virtual (a fixed id that
     * is never a real category) and expands to the union of [pinnedMembers]; null when the
     * playlist has no Italian sports group, so the pill simply does not appear.
     */
    val pinnedCategory: StateFlow<Category?> = pinnedMembers
        .map { members ->
            if (members.isEmpty()) null
            else Category(id = PINNED_CATEGORY_ID, playlistId = members.first().playlistId, name = "Calcio Italia")
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Until the user picks something themselves, the guide opens on the pinned section
    private var userPickedCategory = false

    init {
        viewModelScope.launch {
            pinnedCategory.collect { pinned ->
                if (pinned != null && !userPickedCategory && _uiState.value.selectedCategory == null) {
                    _uiState.update { it.copy(selectedCategory = pinned) }
                }
            }
        }
    }

    // Same profile rules as the Live list: favourites are per profile, child profiles hide adult groups
    private val activeProfile = observeActiveProfile()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Channels stream for selected category
    val channels: StateFlow<List<Channel>> = combine(_uiState, activeProfile) { state, profile ->
        Triple(state.selectedCategory?.id, state.playlistId, profile)
    }
        .distinctUntilChanged()
        .flatMapLatest { (categoryId, playlistId, profile) ->
            getChannelsUseCase(
                // The pinned folder is virtual: load the whole playlist and cut it down below
                categoryId = categoryId.takeIf { it != PINNED_CATEGORY_ID },
                playlistId = playlistId,
                profileId = profile?.id ?: 1L,
                hideAdult = profile?.isChild == true
            )
        }
        .combine(pinnedMembers) { list, members ->
            val order = members.withIndex().associate { (index, c) -> c.id to index }
            when {
                members.isEmpty() -> list
                // "Calcio Italia": only the member groups, in member order (stable within a group)
                _uiState.value.selectedCategory?.id == PINNED_CATEGORY_ID ->
                    list.filter { it.categoryId in order }.sortedBy { order.getValue(it.categoryId) }
                // "Tutti i canali": the same channels go first, the rest keep their order
                _uiState.value.selectedCategory == null ->
                    list.sortedBy { order[it.categoryId] ?: Int.MAX_VALUE }
                else -> list
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // EPG Programs matrix mapped by tvgId. Keyed on (ids, window) only: moving the focus around
    // the grid changes the UI state constantly and must not re-run this query.
    private val visibleRows = MutableStateFlow(0..30)

    /** The grid reports which rows are on screen; guide data is only loaded for those. */
    fun onVisibleRows(first: Int, last: Int) {
        val range = (first - 6).coerceAtLeast(0)..(last + 12)
        if (range != visibleRows.value) visibleRows.value = range
    }

    val programsByChannel: StateFlow<Map<String, List<EpgProgram>>> = combine(
        channels,
        _uiState.map { it.timeWindowStart to it.timeWindowEnd }.distinctUntilChanged(),
        visibleRows
    ) { channelList, window, rows ->
        Triple(
            channelList.asSequence()
                .drop(rows.first)
                .take((rows.last - rows.first + 1).coerceAtLeast(1))
                .mapNotNull { it.tvgId }.filter { it.isNotBlank() }.distinct().toList(),
            window.first,
            window.second
        )
    }
        .distinctUntilChanged()
        .flatMapLatest { (tvgIds, start, end) -> getGridEpgUseCase(tvgIds, start, end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _message = MutableStateFlow<String?>(null)

    /** One-line feedback shown in the banner ("Registrazione programmata", errors…). */
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * OK on a programme: past → catch-up, airing → live, future → record.
     * [onWatchLive] and [onWatchCatchup] navigate; recording is handled here.
     */
    fun activateProgram(
        program: EpgProgram,
        channel: Channel,
        onWatchLive: (Channel) -> Unit,
        onWatchCatchup: (contentKey: String) -> Unit
    ) {
        viewModelScope.launch {
            when (ProgramActions.forProgram(program, System.currentTimeMillis())) {
                ProgramAction.WATCH_LIVE -> onWatchLive(channel)

                ProgramAction.WATCH_CATCHUP -> {
                    val url = buildCatchupUrl(channel, program)
                    if (url == null) {
                        _message.value = "Il catch-up richiede un account Xtream con archivio attivo"
                        return@launch
                    }
                    val profileId = observeActiveProfile().first()?.id ?: 1L
                    val key = "catchup:${channel.id}:${program.startTime}"
                    saveProgress.ensure(
                        WatchProgress(
                            profileId = profileId,
                            contentKey = key,
                            kind = VodKind.MOVIE,
                            playlistId = channel.playlistId,
                            title = program.title,
                            subtitle = channel.cleanName,
                            streamUrl = url
                        )
                    )
                    onWatchCatchup(key)
                }

                ProgramAction.RECORD -> {
                    // Not watching live here (EPG screen), so no live connection to count
                    _message.value = when (val outcome = startRecording(channel, program, watchingLive = false)) {
                        is RecordingOutcome.Scheduled -> "Registrazione programmata: ${program.title}"
                        is RecordingOutcome.NoFreeConnection ->
                            "Connessioni esaurite (${outcome.maxConnections}): impossibile registrare"
                        is RecordingOutcome.Rejected -> outcome.reason
                    }
                }
            }
        }
    }

    /** Sets a notification 2 minutes before a future programme starts. */
    fun remindMe(program: EpgProgram, channel: Channel) {
        val ok = reminderScheduler.schedule(
            channelName = channel.cleanName,
            programTitle = program.title,
            startTimeMs = program.startTime,
            requestKey = "${channel.id}:${program.startTime}"
        )
        _message.value =
            if (ok) "Promemoria impostato: ${program.title}"
            else "Il programma sta per iniziare o è già iniziato"
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    fun selectCategory(category: Category?) {
        userPickedCategory = true
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun onProgramFocused(program: EpgProgram, channel: Channel?) {
        _uiState.update {
            it.copy(
                selectedProgram = program,
                focusedChannel = channel
            )
        }
    }

    fun onChannelFocused(channel: Channel) {
        _uiState.update { it.copy(focusedChannel = channel) }
    }

    fun shiftTimeWindow(hours: Int) {
        _uiState.update { state ->
            val delta = hours * 3600 * 1000L
            val newStart = state.timeWindowStart + delta
            val newEnd = state.timeWindowEnd + delta
            state.copy(timeWindowStart = newStart, timeWindowEnd = newEnd)
        }
    }

    fun resetToNow() {
        val newStart = getInitialWindowStart()
        val newEnd = newStart + (5 * 3600 * 1000L)
        _uiState.update { it.copy(timeWindowStart = newStart, timeWindowEnd = newEnd) }
    }
}
