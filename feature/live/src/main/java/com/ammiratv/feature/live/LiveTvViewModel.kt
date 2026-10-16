package com.ammiratv.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.common.AdultContentFilter
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.AspectRatio
import com.ammiratv.core.model.Category
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.ChannelEpgInfo
import com.ammiratv.core.model.ChannelSetting
import com.ammiratv.core.model.PlaybackEvent
import com.ammiratv.core.model.PlaybackEventType
import com.ammiratv.core.model.PlayerState
import com.ammiratv.core.model.StreamHealthMonitor
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.Profile
import com.ammiratv.core.player.PlayerEngine
import com.ammiratv.domain.usecase.ChannelSettingUseCase
import com.ammiratv.domain.usecase.RecommendBufferUseCase
import com.ammiratv.domain.usecase.RecordPlaybackEventUseCase
import com.ammiratv.domain.usecase.RecordingOutcome
import com.ammiratv.domain.usecase.StartRecordingUseCase
import com.ammiratv.domain.usecase.EpgSyncCoordinator
import com.ammiratv.domain.usecase.EpgSyncState
import com.ammiratv.domain.usecase.FindAlternativeStreamUseCase
import com.ammiratv.domain.usecase.GetCategoriesUseCase
import com.ammiratv.domain.usecase.GetChannelByIdUseCase
import com.ammiratv.domain.usecase.GetChannelsUseCase
import com.ammiratv.domain.usecase.GetNowAndNextUseCase
import com.ammiratv.domain.usecase.GetPlaylistsUseCase
import com.ammiratv.domain.usecase.ObserveActiveProfileUseCase
import com.ammiratv.domain.usecase.ParentalGuard
import com.ammiratv.domain.usecase.ToggleFavoriteUseCase
import com.ammiratv.domain.usecase.UniversalSearchResult
import com.ammiratv.domain.usecase.UniversalSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** A number being typed with the remote: digits so far and, when it exists, the channel it points to. */
data class NumberEntryState(
    val digits: String,
    val previewName: String? = null,
    /** Set when the typed number matches nothing; shown briefly, then cleared. */
    val notFound: Boolean = false
)

data class LiveTvUiState(
    val playlistId: Long = 0L,
    val selectedCategory: Category? = null,
    val isFavoritesSelected: Boolean = false,
    val playingChannel: Channel? = null,
    val isOverlayVisible: Boolean = false,
    val isQuickPanelOpen: Boolean = false,
    val failoverMessage: String? = null,
    val isAudioMenuVisible: Boolean = false,
    val isSubtitleMenuVisible: Boolean = false,
    /** True when the profile's daily viewing time is used up. */
    val isLimitReached: Boolean = false
)

/** What decides which channels are listed; kept apart from UI flags so toggling an overlay never re-queries the DB. */
private data class ListSelection(
    val playlistId: Long,
    val categoryId: Long?,
    val profileId: Long,
    val hideAdult: Boolean,
    val mergeDuplicates: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getChannelsUseCase: GetChannelsUseCase,
    private val getNowAndNextUseCase: GetNowAndNextUseCase,
    private val findAlternativeStreamUseCase: FindAlternativeStreamUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val getChannelByIdUseCase: GetChannelByIdUseCase,
    private val universalSearchUseCase: UniversalSearchUseCase,
    observeActiveProfileUseCase: ObserveActiveProfileUseCase,
    getPlaylistsUseCase: GetPlaylistsUseCase,
    private val parentalGuard: ParentalGuard,
    epgSyncCoordinator: EpgSyncCoordinator,
    private val recordPlaybackEvent: RecordPlaybackEventUseCase,
    private val recommendBuffer: RecommendBufferUseCase,
    private val startRecording: StartRecordingUseCase,
    private val findChannelByNumber: com.ammiratv.domain.usecase.FindChannelByNumberUseCase,
    private val channelSettings: ChannelSettingUseCase,
    private val officialResolver: com.ammiratv.domain.usecase.OfficialStreamResolver,
    val playerEngine: PlayerEngine,
    private val preferences: AmmiraTvPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    val playerState: StateFlow<PlayerState> = playerEngine.state

    /** Progress of the background guide download, shown as a small hint in the top bar. */
    val epgSyncState: StateFlow<EpgSyncState> = epgSyncCoordinator.state

    val activeProfile: StateFlow<Profile?> = observeActiveProfileUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasParentalPin: StateFlow<Boolean> = parentalGuard.hasPin
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Sources shown as quick-switch chips in the OK panel. */
    val playlists: StateFlow<List<Playlist>> = getPlaylistsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var overlayDismissJob: Job? = null

    private val selection = combine(
        _uiState,
        activeProfile,
        preferences.mergeDuplicates
    ) { state, profile, merge ->
        ListSelection(
            playlistId = state.playlistId,
            categoryId = when {
                state.isFavoritesSelected -> GetChannelsUseCase.FAVORITES_CATEGORY
                else -> state.selectedCategory?.id
            },
            profileId = profile?.id ?: DEFAULT_PROFILE_ID,
            hideAdult = profile?.isChild == true,
            mergeDuplicates = merge
        )
    }.distinctUntilChanged()

    // Categories stream for current playlist (adult groups removed for child profiles)
    val categories: StateFlow<List<Category>> = selection
        .map { it.playlistId to it.hideAdult }
        .distinctUntilChanged()
        .flatMapLatest { (playlistId, hideAdult) ->
            getCategoriesUseCase(playlistId).map { list ->
                if (hideAdult) list.filterNot { AdultContentFilter.isAdult(it.name) } else list
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** TV-style numbering on/off (when off, rows show the plain list position as before). */
    val numberingEnabled: StateFlow<Boolean> = preferences.numberingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Channels stream for selected category or favorites; optionally ordered by TV number.
    // Zapping up/down walks this same list, so it follows the numeric order when that is on.
    val channels: StateFlow<List<Channel>> = selection
        .flatMapLatest { sel ->
            getChannelsUseCase(sel.categoryId, sel.playlistId, sel.profileId, sel.hideAdult, sel.mergeDuplicates)
                // A burst of writes (guide matching, numbering) must cost one reload, not fifty
                .debounce(250)
        }
        .combine(preferences.sortByNumber) { list, sortByNumber ->
            if (!sortByNumber) list
            // Numbered channels first, ascending; the rest keep their order (sort is stable)
            else list.sortedBy { it.channelNumber ?: Int.MAX_VALUE }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Number entry from the remote ─────────────────────────────────────
    private val _numberEntry = MutableStateFlow<NumberEntryState?>(null)
    val numberEntry: StateFlow<NumberEntryState?> = _numberEntry.asStateFlow()
    private var numberEntryJob: Job? = null

    /** A digit key was pressed: extend the number, show a preview, and (re)start the timeout. */
    fun onDigit(digit: Int) {
        if (digit !in 0..9) return
        noteInteraction()
        val digits = ((_numberEntry.value?.takeIf { !it.notFound }?.digits).orEmpty() + digit).takeLast(MAX_NUMBER_DIGITS)
        _numberEntry.value = NumberEntryState(digits)

        numberEntryJob?.cancel()
        numberEntryJob = viewModelScope.launch {
            // Preview first (cheap query), so the name appears while the user is still typing
            val number = digits.toInt()
            val preview = if (numberingEnabled.value && number > 0) {
                findChannelByNumber(_uiState.value.playlistId, number)?.cleanName
            } else null
            _numberEntry.value = NumberEntryState(digits, previewName = preview)

            delay(preferences.numberEntryTimeoutMs.first().toLong())
            commitNumberEntry()
        }
    }

    /** OK pressed while a number is being typed: tune now instead of waiting for the timeout. */
    fun confirmNumberEntry() {
        numberEntryJob?.cancel()
        numberEntryJob = viewModelScope.launch { commitNumberEntry() }
    }

    private suspend fun commitNumberEntry() {
        val entry = _numberEntry.value ?: return
        val number = entry.digits.toIntOrNull() ?: 0
        val channel = if (number > 0) findChannelByNumber(_uiState.value.playlistId, number) else null
        if (channel != null) {
            _numberEntry.value = null
            playChannel(channel)
        } else {
            // Unknown number: say so, change nothing, never block
            _numberEntry.value = entry.copy(notFound = true)
            delay(NOT_FOUND_MESSAGE_MS)
            if (_numberEntry.value?.notFound == true) _numberEntry.value = null
        }
    }

    private val requestedGuideIds = HashSet<String>()

    /** Loads now/next for the rows currently visible (plus a small margin). Called by the screen. */
    fun onVisibleGuideIds(tvgIds: List<String>) {
        val missing = tvgIds.filter { it.isNotBlank() && requestedGuideIds.add(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = runCatching { getNowAndNextUseCase(missing) }.getOrDefault(emptyMap())
            if (loaded.isNotEmpty()) _nowAndNext.update { it + loaded }
        }
    }

    private val _nowAndNext = MutableStateFlow<Map<String, ChannelEpgInfo>>(emptyMap())
    val nowAndNext: StateFlow<Map<String, ChannelEpgInfo>> = _nowAndNext.asStateFlow()

    // ── OK quick panel: search + account chips ───────────────────────────
    private val _quickQuery = MutableStateFlow("")
    val quickQuery: StateFlow<String> = _quickQuery.asStateFlow()

    val quickResults: StateFlow<UniversalSearchResult> = _quickQuery
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q -> flow { emit(universalSearchUseCase(q)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UniversalSearchResult())

    private var retryCount = 0
    private var isFailingOver = false

    /** Watches the player for slow starts, rebuffers and frozen pictures. */
    private val healthMonitor = StreamHealthMonitor()

    // ── Sleep timer & "still watching?" ──────────────────────────────────
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()
    private var sleepJob: Job? = null

    private val _stillWatchingPrompt = MutableStateFlow(false)
    val stillWatchingPrompt: StateFlow<Boolean> = _stillWatchingPrompt.asStateFlow()
    private var lastInteractionAt = System.currentTimeMillis()
    private var stillWatchingTimeoutJob: Job? = null

    val cinemaMode: StateFlow<Boolean> = preferences.cinemaMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Inactivity guard: after 4h without any remote interaction ask, then stop if unanswered
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val idleMs = System.currentTimeMillis() - lastInteractionAt
                if (_uiState.value.playingChannel != null && idleMs >= INACTIVITY_LIMIT_MS &&
                    !_stillWatchingPrompt.value
                ) {
                    _stillWatchingPrompt.value = true
                    stillWatchingTimeoutJob = launch {
                        delay(STILL_WATCHING_TIMEOUT_MS)
                        _stillWatchingPrompt.value = false
                        stopPlayback()
                    }
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            preferences.activePlaylistId.collect { id ->
                _uiState.update { it.copy(playlistId = id) }
            }
        }

        // The list changed: forget the guide rows; the screen asks again for the ones it shows.
        // (Querying the guide for every channel of a 50k list crashed older Android versions with
        // "too many SQL variables" and made the others crawl.)
        viewModelScope.launch {
            channels.collect {
                requestedGuideIds.clear()
                _nowAndNext.value = emptyMap()
            }
        }

        // Automatic failover monitor
        viewModelScope.launch {
            playerEngine.state.collect { pState ->
                _uiState.value.playingChannel?.let { channel ->
                    healthMonitor.onState(
                        nowMs = System.currentTimeMillis(),
                        isPlaying = pState.isPlaying,
                        isBuffering = pState.isBuffering,
                        isError = pState.isError,
                        positionMs = pState.currentPositionMs
                    ).forEach { signal -> onHealthSignal(channel, signal) }
                }

                if (pState.isError && !isFailingOver) {
                    val currentChannel = _uiState.value.playingChannel ?: return@collect
                    handlePlaybackFailure(currentChannel)
                } else if (pState.isPlaying && !pState.isBuffering) {
                    retryCount = 0
                    triedServers.clear()
                    isFailingOver = false
                    _uiState.value.playingChannel?.let { ch ->
                        findAlternativeStreamUseCase.recordStreamSuccess(ch.id)
                    }
                }
            }
        }

        // Daily viewing limit (child profiles): counts only while something is actually playing.
        viewModelScope.launch {
            while (true) {
                delay(LIMIT_TICK_MS)
                val profile = activeProfile.value ?: continue
                val limit = profile.dailyLimitMinutes ?: continue
                if (!playerState.value.isPlaying) continue

                val day = today()
                preferences.addWatchedMs(profile.id, day, LIMIT_TICK_MS)
                if (preferences.watchedMsToday(profile.id, day).first() >= limit * 60_000L) {
                    stopPlayback()
                    _uiState.update { it.copy(isLimitReached = true) }
                }
            }
        }
    }

    /** Channels whose RaiPlay stream failed this session: they go back to the playlist. */
    private val officialFailed = HashSet<Long>()
    private var playingOfficial = false

    /**
     * RaiPlay is the FIRST choice for the Rai channels (free, official, Italian IP only). It is
     * only used when it is really live right now; otherwise, or if it later fails, the playlist
     * stream is played. @return the address to open.
     */
    private suspend fun chooseStream(channel: Channel): String {
        playingOfficial = false
        if (channel.id !in officialFailed && preferences.officialFallback.first()) {
            val official = officialResolver.resolve(channel.cleanName)
            if (official != null) {
                playingOfficial = true
                playerEngine.setNowPlaying(official.channel, official.provider.label)
                return official.playUrl
            }
        }
        playerEngine.setNowPlaying(channel.cleanName, channel.groupTitle)
        return channel.streamUrl
    }

    private val triedServers = HashMap<Long, MutableSet<String>>()

    /**
     * The playlist may list several servers. When the current one fails, the same stream path is
     * tried on the next one. @return true when an attempt was started.
     */
    private suspend fun tryAlternateServer(channel: Channel): Boolean {
        val playlist = playlists.value.firstOrNull { it.id == channel.playlistId } ?: return false
        if (playlist.alternateUrls.isEmpty()) return false
        val current = com.ammiratv.core.model.ServerList.baseOf(channel.streamUrl) ?: return false
        val bases = (listOf(playlist.url) + playlist.alternateUrls)
            .mapNotNull { com.ammiratv.core.model.ServerList.baseOf(it) }.distinct()
        val tried = triedServers.getOrPut(channel.id) { mutableSetOf(current) }
        val next = bases.firstOrNull { it !in tried } ?: return false
        tried += next

        val url = channel.streamUrl.replaceFirst(Regex("^https?://[^/?#]+", RegexOption.IGNORE_CASE), next)
        _uiState.update { it.copy(failoverMessage = "Provo un altro server del provider...") }
        delay(1000)
        healthMonitor.onNewStream(System.currentTimeMillis())
        playerEngine.setNowPlaying(channel.cleanName, channel.groupTitle)
        playerEngine.prepare(url)
        playerEngine.play()
        return true
    }

    private fun handlePlaybackFailure(channel: Channel) {
        viewModelScope.launch {
            // RaiPlay was playing and failed: straight to the playlist stream of the same channel
            if (playingOfficial) {
                officialFailed.add(channel.id)
                playingOfficial = false
                retryCount = 0
                _uiState.update { it.copy(failoverMessage = "RaiPlay non disponibile: uso la playlist...") }
                delay(800)
                healthMonitor.onNewStream(System.currentTimeMillis())
                playerEngine.setNowPlaying(channel.cleanName, channel.groupTitle)
                playerEngine.prepare(channel.streamUrl)
                playerEngine.play()
                return@launch
            }
            findAlternativeStreamUseCase.recordStreamFailure(channel.id)
            if (retryCount < 2) {
                retryCount++
                _uiState.update { it.copy(failoverMessage = "Stream interrotto: riconnessione in corso ($retryCount/2)...") }
                delay(1500)
                healthMonitor.onNewStream(System.currentTimeMillis())
                playerEngine.setNowPlaying(channel.cleanName, channel.groupTitle)
                playerEngine.prepare(channel.streamUrl)
                playerEngine.play()
            } else {
                // Same channel on another server (DNS) of the same provider, before other channels
                if (tryAlternateServer(channel)) return@launch
                val alternatives = findAlternativeStreamUseCase(channel)
                if (alternatives.isNotEmpty()) {
                    val backup = alternatives.first()
                    isFailingOver = true
                    retryCount = 0
                    _uiState.update {
                        it.copy(failoverMessage = "Passaggio automatico a backup: ${backup.cleanName}...")
                    }
                    delay(1500)
                    playChannel(backup)
                } else {
                    _uiState.update {
                        it.copy(failoverMessage = "Errore stream. Nessuna sorgente alternativa trovata.")
                    }
                }
            }
        }
    }

    /** TV "full screen": fill the whole screen (cropping the edges) or return to the original shape. */
    fun toggleFill() {
        noteInteraction()
        val next = if (playerState.value.aspectRatio == AspectRatio.FILL) AspectRatio.FIT else AspectRatio.FILL
        playerEngine.setAspectRatio(next)
        updateChannelSetting { it.copy(aspectRatio = next) }
    }

    fun cycleAspectRatio() {
        noteInteraction()
        val current = playerState.value.aspectRatio
        val next = when (current) {
            AspectRatio.FIT -> AspectRatio.FILL
            AspectRatio.FILL -> AspectRatio.ZOOM
            AspectRatio.ZOOM -> AspectRatio.STRETCH
            AspectRatio.STRETCH -> AspectRatio.FIT
            else -> AspectRatio.FIT
        }
        playerEngine.setAspectRatio(next)
        updateChannelSetting { it.copy(aspectRatio = next) }
    }

    /** Applies [change] to the stored setting of the channel being watched ("profili per canale"). */
    private fun updateChannelSetting(change: (ChannelSetting) -> ChannelSetting) {
        val url = _uiState.value.playingChannel?.streamUrl ?: return
        viewModelScope.launch {
            val current = channelSettings.get(url) ?: ChannelSetting(streamUrl = url)
            channelSettings.save(change(current))
        }
    }

    /** Restores what the user chose last time on this channel once the stream's tracks are known. */
    private suspend fun applyChannelSetting(channel: Channel) {
        val setting = channelSettings.get(channel.streamUrl)
        playerEngine.setAspectRatio(setting?.aspectRatio ?: AspectRatio.FIT)
        if (setting?.audioLanguage == null && setting?.subtitleLanguage == null) return

        repeat(20) { // up to ~10 s for the tracks to show up
            val s = playerEngine.state.value
            if (s.audioTracks.isNotEmpty() || s.subtitleTracks.isNotEmpty()) {
                setting.audioLanguage?.let { lang ->
                    s.audioTracks.firstOrNull { it.language.equals(lang, true) }
                        ?.let { playerEngine.setAudioTrack(it.index) }
                }
                setting.subtitleLanguage?.let { lang ->
                    s.subtitleTracks.firstOrNull { it.language.equals(lang, true) }
                        ?.let { playerEngine.setSubtitleTrack(it.index) }
                }
                return
            }
            delay(500)
        }
    }

    private fun onHealthSignal(channel: Channel, signal: StreamHealthMonitor.Signal) {
        val (type, duration) = when (signal) {
            is StreamHealthMonitor.Signal.StartOk -> PlaybackEventType.START_OK to signal.timeToFirstFrameMs
            StreamHealthMonitor.Signal.StartFailed -> PlaybackEventType.START_FAILED to 0L
            is StreamHealthMonitor.Signal.Rebuffer -> PlaybackEventType.REBUFFER to signal.durationMs
            StreamHealthMonitor.Signal.Stalled -> PlaybackEventType.STALL to 0L
        }
        viewModelScope.launch {
            recordPlaybackEvent(
                PlaybackEvent(
                    playlistId = channel.playlistId,
                    channelId = channel.id,
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    durationMs = duration
                )
            )
        }

        // Frozen picture with no error: the player never reports it, so restart the stream.
        if (signal == StreamHealthMonitor.Signal.Stalled) {
            viewModelScope.launch {
                findAlternativeStreamUseCase.recordStreamFailure(channel.id)
                _uiState.update { it.copy(failoverMessage = "Immagine ferma: riavvio automatico dello stream…") }
                healthMonitor.onNewStream(System.currentTimeMillis())
                playerEngine.setNowPlaying(channel.cleanName, channel.groupTitle)
                playerEngine.prepare(channel.streamUrl)
                playerEngine.play()
            }
        }
    }

    // ── Recording ────────────────────────────────────────────────────────

    /** Records the channel being watched until the end of the current programme (or 60 min). */
    fun recordCurrentChannel() {
        val channel = _uiState.value.playingChannel ?: return
        val program = _nowAndNext.value[channel.tvgId]?.currentProgram
        viewModelScope.launch {
            val message = when (val outcome = startRecording(channel, program, watchingLive = true)) {
                is RecordingOutcome.Scheduled -> "Registrazione avviata: ${program?.title ?: channel.cleanName}"
                is RecordingOutcome.NoFreeConnection ->
                    "Impossibile registrare: il tuo abbonamento permette ${outcome.maxConnections} connessione/i, tutte in uso"
                is RecordingOutcome.Rejected -> outcome.reason
            }
            _uiState.update { it.copy(failoverMessage = message) }
        }
    }

    // ── Sleep timer ──────────────────────────────────────────────────────

    /** Off → 30 → 60 → 90 → 120 → off. */
    fun cycleSleepTimer() {
        noteInteraction()
        val next = when (_sleepTimerMinutes.value) {
            null -> 30
            30 -> 60
            60 -> 90
            90 -> 120
            else -> null
        }
        _sleepTimerMinutes.value = next
        sleepJob?.cancel()
        if (next != null) {
            sleepJob = viewModelScope.launch {
                delay(next * 60_000L)
                _sleepTimerMinutes.value = null
                stopPlayback()
            }
        }
    }

    fun confirmStillWatching() {
        _stillWatchingPrompt.value = false
        stillWatchingTimeoutJob?.cancel()
        noteInteraction()
    }

    private fun noteInteraction() {
        lastInteractionAt = System.currentTimeMillis()
    }

    fun togglePlayPause() {
        noteInteraction()
        if (playerState.value.isPlaying) {
            playerEngine.pause()
        } else {
            playerEngine.play()
        }
    }

    fun seekRelative(deltaMs: Long) {
        val newPos = (playerState.value.currentPositionMs + deltaMs).coerceAtLeast(0)
        playerEngine.seekTo(newPos)
    }

    fun jumpToLive() {
        playerEngine.jumpToLive()
    }

    fun setAudioTrack(index: Int) {
        val language = playerState.value.audioTracks.firstOrNull { it.index == index }?.language
        playerEngine.setAudioTrack(index)
        _uiState.update { it.copy(isAudioMenuVisible = false) }
        if (language != null) updateChannelSetting { it.copy(audioLanguage = language) }
    }

    fun setSubtitleTrack(index: Int) {
        val language = playerState.value.subtitleTracks.firstOrNull { it.index == index }?.language
        playerEngine.setSubtitleTrack(index)
        _uiState.update { it.copy(isSubtitleMenuVisible = false) }
        // -1 = subtitles off: remembered too, as "no preference" would re-enable them next time
        updateChannelSetting { it.copy(subtitleLanguage = language) }
    }

    fun toggleAudioMenu() {
        _uiState.update { it.copy(isAudioMenuVisible = !it.isAudioMenuVisible, isSubtitleMenuVisible = false) }
    }

    fun toggleSubtitleMenu() {
        _uiState.update { it.copy(isSubtitleMenuVisible = !it.isSubtitleMenuVisible, isAudioMenuVisible = false) }
    }

    fun dismissTrackMenus() {
        _uiState.update { it.copy(isAudioMenuVisible = false, isSubtitleMenuVisible = false) }
    }

    fun clearFailoverMessage() {
        _uiState.update { it.copy(failoverMessage = null) }
    }

    fun dismissLimitReached() {
        _uiState.update { it.copy(isLimitReached = false) }
    }

    fun selectCategory(category: Category?) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                isFavoritesSelected = false
            )
        }
    }

    fun selectFavorites() {
        _uiState.update {
            it.copy(
                selectedCategory = null,
                isFavoritesSelected = true
            )
        }
    }

    fun playChannel(channel: Channel) {
        viewModelScope.launch {
            if (isOverDailyLimit()) {
                _uiState.update { it.copy(isLimitReached = true) }
                return@launch
            }
            noteInteraction()
            // Cinema mode keeps the screen clean: no info bar popping up at every zap
            val showBar = !cinemaMode.value
            _uiState.update {
                it.copy(
                    playingChannel = channel,
                    isOverlayVisible = showBar,
                    isQuickPanelOpen = false
                )
            }

            // "Buffer che impara": enlarge the buffer before the hours that usually go wrong
            applyLearnedBuffer(channel)

            healthMonitor.onNewStream(System.currentTimeMillis())
            val address = chooseStream(channel)
            playerEngine.prepare(address)
            playerEngine.play()
            if (showBar) scheduleOverlayDismiss()
            preferences.setLastChannelId(channel.id)

            launch { applyChannelSetting(channel) }
        }
    }

    private suspend fun applyLearnedBuffer(channel: Channel) {
        if (!preferences.adaptiveBuffer.first()) {
            playerEngine.setBufferOverrideMs(null)
            return
        }
        val configured = preferences.bufferSizeMs.first()
        val recommended = recommendBuffer(channel.playlistId, configured)
        playerEngine.setBufferOverrideMs(if (recommended != configured) recommended else null)
    }

    /** Starts a channel requested from another screen (EPG grid, search results). */
    fun playChannelById(channelId: Long) {
        viewModelScope.launch {
            getChannelByIdUseCase(channelId)?.let { playChannel(it) }
        }
    }

    fun zapNext() {
        val currentChannels = channels.value
        val current = _uiState.value.playingChannel ?: return
        if (currentChannels.isEmpty()) return

        val currentIndex = currentChannels.indexOfFirst { it.id == current.id }
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % currentChannels.size else 0
        playChannel(currentChannels[nextIndex])
    }

    fun zapPrevious() {
        val currentChannels = channels.value
        val current = _uiState.value.playingChannel ?: return
        if (currentChannels.isEmpty()) return

        val currentIndex = currentChannels.indexOfFirst { it.id == current.id }
        val prevIndex = if (currentIndex != -1) (currentIndex - 1 + currentChannels.size) % currentChannels.size else 0
        playChannel(currentChannels[prevIndex])
    }

    fun toggleFavorite(channel: Channel) {
        val profileId = activeProfile.value?.id ?: DEFAULT_PROFILE_ID
        viewModelScope.launch {
            toggleFavoriteUseCase(profileId, channel.id, channel.isFavorite)
        }
    }

    // ── Parental PIN ─────────────────────────────────────────────────────

    fun verifyPin(pin: String): Boolean = parentalGuard.verify(pin)

    /** Whether opening settings must be gated: child profile + a PIN exists. */
    fun requiresPinForSettings(): Boolean =
        activeProfile.value?.isChild == true && parentalGuard.hasPinNow()

    // ── Overlays ─────────────────────────────────────────────────────────

    fun toggleOverlay() {
        val newState = !_uiState.value.isOverlayVisible
        _uiState.update { it.copy(isOverlayVisible = newState) }
        if (newState) {
            scheduleOverlayDismiss()
        } else {
            overlayDismissJob?.cancel()
        }
    }

    fun hideOverlay() {
        _uiState.update { it.copy(isOverlayVisible = false) }
        overlayDismissJob?.cancel()
    }

    /**
     * OK on the remote: the panel opens over the still-playing video. The auto-hiding
     * info bar is dismissed so the two never overlap.
     */
    fun openQuickPanel() {
        overlayDismissJob?.cancel()
        _uiState.update { it.copy(isQuickPanelOpen = true, isOverlayVisible = false) }
    }

    fun closeQuickPanel() {
        _quickQuery.value = ""
        _uiState.update { it.copy(isQuickPanelOpen = false) }
    }

    fun setQuickQuery(value: String) {
        _quickQuery.value = value
    }

    /** Switches the active source from the panel; playback continues untouched. */
    fun switchPlaylist(playlistId: Long) {
        viewModelScope.launch { preferences.setActivePlaylistId(playlistId) }
    }

    fun stopPlayback() {
        playerEngine.stop()
        _uiState.update {
            it.copy(playingChannel = null, isOverlayVisible = false, isQuickPanelOpen = false)
        }
    }

    private suspend fun isOverDailyLimit(): Boolean {
        val profile = activeProfile.value ?: return false
        val limit = profile.dailyLimitMinutes ?: return false
        return preferences.watchedMsToday(profile.id, today()).first() >= limit * 60_000L
    }

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** A key was pressed while the controls are up: give the user more time before they hide. */
    fun keepOverlayAlive() {
        if (_uiState.value.isOverlayVisible) scheduleOverlayDismiss()
    }

    private fun scheduleOverlayDismiss() {
        overlayDismissJob?.cancel()
        overlayDismissJob = viewModelScope.launch {
            delay(8000)
            _uiState.update { it.copy(isOverlayVisible = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerEngine.release()
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = 1L
        const val MAX_NUMBER_DIGITS = 4
        const val NOT_FOUND_MESSAGE_MS = 1_500L
        const val LIMIT_TICK_MS = 30_000L
        const val INACTIVITY_LIMIT_MS = 4 * 3600 * 1000L
        const val STILL_WATCHING_TIMEOUT_MS = 60_000L
    }
}
