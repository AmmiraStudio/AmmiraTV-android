package com.ammiratv.feature.multiview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.MultiViewBudget
import com.ammiratv.core.model.MultiViewLayout
import com.ammiratv.domain.usecase.GetChannelByIdUseCase
import com.ammiratv.domain.usecase.GetChannelsUseCase
import com.ammiratv.domain.usecase.GetPlaylistByIdUseCase
import com.ammiratv.domain.usecase.ObserveActiveProfileUseCase
import com.ammiratv.domain.usecase.RefreshProviderInfoUseCase
import com.ammiratv.domain.usecase.SearchChannelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MultiViewUiState(
    val tiles: List<Channel?> = List(MultiViewLayout.SLOTS) { null },
    val focusedIndex: Int = 0,
    val maxTiles: Int = MultiViewLayout.SLOTS,
    val message: String? = null,
    val pickerSlot: Int? = null,
    val pickerResults: List<Channel> = emptyList(),
    val showLayouts: Boolean = false,
    val askLayoutName: Boolean = false,
    /** Bumped whenever a player is created or released, so the UI re-binds its PlayerViews. */
    val playerRevision: Int = 0
)

/**
 * 2x2 multi-view. Each tile owns its ExoPlayer (the shared PlayerEngine is single-stream).
 *
 * Rules that keep it from misbehaving:
 *  - never more simultaneous streams than the provider's connection limit;
 *  - audio only from the focused tile, and only that tile may use full resolution:
 *    secondary tiles are capped to ~360p so 4 hardware decoders are not needed at full rate.
 */
@UnstableApi
@HiltViewModel
class MultiViewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AmmiraTvPreferences,
    private val getChannels: GetChannelsUseCase,
    private val searchChannels: SearchChannelsUseCase,
    private val getChannelById: GetChannelByIdUseCase,
    private val getPlaylistById: GetPlaylistByIdUseCase,
    private val refreshProviderInfo: RefreshProviderInfoUseCase,
    private val observeActiveProfile: ObserveActiveProfileUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MultiViewUiState())
    val state: StateFlow<MultiViewUiState> = _state.asStateFlow()

    private val players = arrayOfNulls<ExoPlayer>(MultiViewLayout.SLOTS)

    val savedLayouts: StateFlow<List<MultiViewLayout>> = preferences.multiViewLayouts
        .map { set -> set.mapNotNull(MultiViewLayout::decode).sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Fresh limit from the provider (auth only, no stream opened)
            val playlistId = preferences.activePlaylistId.first()
            val playlist = getPlaylistById(playlistId)
            if (playlist != null) {
                refreshProviderInfo(playlist)
                val fresh = getPlaylistById(playlistId) ?: playlist
                val max = MultiViewBudget.maxTiles(fresh.maxConnections)
                providerMax = max
                _state.update { it.copy(maxTiles = minOf(max, deviceLimit)) }
                if (max < 2) {
                    _state.update {
                        it.copy(message = "Il tuo abbonamento permette una sola connessione: il multi-view mostra un solo riquadro")
                    }
                }
            }
        }
    }

    fun playerFor(slot: Int): ExoPlayer? = players.getOrNull(slot)

    private var providerMax = MultiViewLayout.SLOTS
    private var deviceLimit = MultiViewLayout.SLOTS

    /**
     * Caps simultaneous streams to what the device's hardware decoders can really run (a 1 GB
     * Fire TV Stick cannot decode four streams). Combined with the provider's connection limit.
     */
    fun setDeviceLimit(decoders: Int) {
        val limit = decoders.coerceIn(1, MultiViewLayout.SLOTS)
        if (limit == deviceLimit) return
        deviceLimit = limit
        val max = minOf(providerMax, deviceLimit)
        _state.update {
            it.copy(
                maxTiles = max,
                message = if (limit < MultiViewLayout.SLOTS)
                    "Questo dispositivo regge al massimo $limit flussi insieme: il multi-view è limitato a $limit riquadri"
                else it.message
            )
        }
    }

    // ── Tiles ────────────────────────────────────────────────────────────

    fun setTile(slot: Int, channel: Channel) {
        val s = _state.value
        val occupiedElsewhere = s.tiles.withIndex().count { (i, c) -> c != null && i != slot }
        if (occupiedElsewhere >= s.maxTiles) {
            _state.update {
                it.copy(message = "Limite di connessioni raggiunto (${s.maxTiles}): rimuovi un riquadro prima")
            }
            return
        }

        releasePlayer(slot)
        val player = ExoPlayer.Builder(context)
            .setLoadControl(
                // Small buffers: four players share memory and the network
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_500, 8_000, 500, 1_000)
                    .build()
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(channel.streamUrl))
                prepare()
                playWhenReady = true
            }
        players[slot] = player

        _state.update {
            it.copy(
                tiles = it.tiles.toMutableList().also { list -> list[slot] = channel },
                pickerSlot = null,
                playerRevision = it.playerRevision + 1,
                message = null
            )
        }
        applyFocusRules()
    }

    fun clearTile(slot: Int) {
        releasePlayer(slot)
        _state.update {
            it.copy(
                tiles = it.tiles.toMutableList().also { list -> list[slot] = null },
                playerRevision = it.playerRevision + 1
            )
        }
    }

    fun setFocused(index: Int) {
        if (_state.value.focusedIndex == index) return
        _state.update { it.copy(focusedIndex = index) }
        applyFocusRules()
    }

    /** Audio only on the focused tile; full resolution only there too. */
    private fun applyFocusRules() {
        val focused = _state.value.focusedIndex
        players.forEachIndexed { index, player ->
            player ?: return@forEachIndexed
            val isFocused = index == focused
            player.volume = if (isFocused) 1f else 0f
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(
                    if (isFocused) Int.MAX_VALUE else SECONDARY_WIDTH,
                    if (isFocused) Int.MAX_VALUE else SECONDARY_HEIGHT
                )
                .setMaxVideoBitrate(if (isFocused) Int.MAX_VALUE else SECONDARY_MAX_BITRATE)
                .build()
        }
    }

    private fun releasePlayer(slot: Int) {
        players[slot]?.release()
        players[slot] = null
    }

    // ── Channel picker ───────────────────────────────────────────────────

    fun openPicker(slot: Int) {
        _state.update { it.copy(pickerSlot = slot, pickerResults = emptyList()) }
        viewModelScope.launch {
            val profile = observeActiveProfile().first()
            val playlistId = preferences.activePlaylistId.first()
            // Favourites first: that's what people multi-view
            val favorites = getChannels(
                categoryId = GetChannelsUseCase.FAVORITES_CATEGORY,
                playlistId = playlistId,
                profileId = profile?.id ?: 1L,
                hideAdult = profile?.isChild == true
            ).first()
            _state.update { it.copy(pickerResults = favorites.take(100)) }
        }
    }

    fun searchPicker(query: String) {
        viewModelScope.launch {
            val results = if (query.trim().length >= 2) searchChannels(query) else emptyList()
            _state.update { it.copy(pickerResults = results) }
        }
    }

    fun closePicker() = _state.update { it.copy(pickerSlot = null) }

    // ── Layouts ──────────────────────────────────────────────────────────

    fun askLayoutName() = _state.update { it.copy(askLayoutName = true) }
    fun dismissLayoutName() = _state.update { it.copy(askLayoutName = false) }
    fun toggleLayouts() = _state.update { it.copy(showLayouts = !it.showLayouts) }

    fun saveLayout(name: String) {
        val layout = MultiViewLayout(name.trim().ifEmpty { "Layout" }, _state.value.tiles.map { it?.id })
        viewModelScope.launch {
            // Same name replaces the old layout
            preferences.saveMultiViewLayout(layout.encode(), replacePrefix = layout.name + "|")
            _state.update { it.copy(askLayoutName = false, message = "Layout \"${layout.name}\" salvato") }
        }
    }

    fun deleteLayout(layout: MultiViewLayout) {
        viewModelScope.launch { preferences.deleteMultiViewLayout(layout.encode()) }
    }

    fun loadLayout(layout: MultiViewLayout) {
        _state.update { it.copy(showLayouts = false) }
        viewModelScope.launch {
            layout.channelIds.forEachIndexed { slot, id ->
                if (id == null) {
                    clearTile(slot)
                } else {
                    getChannelById(id)?.let { setTile(slot, it) }
                }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        players.indices.forEach { releasePlayer(it) }
        super.onCleared()
    }

    private companion object {
        const val SECONDARY_WIDTH = 640
        const val SECONDARY_HEIGHT = 360
        const val SECONDARY_MAX_BITRATE = 1_200_000
    }
}
