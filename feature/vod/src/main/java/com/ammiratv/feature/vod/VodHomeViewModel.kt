package com.ammiratv.feature.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.model.VodCategory
import com.ammiratv.core.model.VodItem
import com.ammiratv.core.model.VodKind
import com.ammiratv.core.model.WatchProgress
import com.ammiratv.domain.usecase.GetPlaylistByIdUseCase
import com.ammiratv.domain.usecase.GetVodCatalogUseCase
import com.ammiratv.domain.usecase.ObserveActiveProfileUseCase
import com.ammiratv.domain.usecase.ObserveContinueWatchingUseCase
import com.ammiratv.domain.usecase.ObserveWatchProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Id of the pinned virtual "Italiani" folder (never a real provider category id). */
const val PINNED_ITALIAN = "__italian__"

/** Italian content is recognised by ItalianCategories, shared with the Calcio Italia folder. */
fun isItalianCategory(name: String): Boolean = com.ammiratv.core.model.ItalianCategories.isItalian(name)

data class VodHomeUiState(
    val kind: VodKind = VodKind.MOVIE,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True when the active source can't serve a catalogue (M3U, or nothing configured). */
    val unsupported: Boolean = false,
    val categories: List<VodCategory> = emptyList(),
    /** Provider categories that hold Italian content; the pinned "Italiani" folder is their union. */
    val italianCategoryIds: Set<String> = emptySet(),
    val selectedCategoryId: String? = null,
    val query: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VodHomeViewModel @Inject constructor(
    private val getPlaylistById: GetPlaylistByIdUseCase,
    private val getVodCatalog: GetVodCatalogUseCase,
    observeContinueWatching: ObserveContinueWatchingUseCase,
    observeWatchProgress: ObserveWatchProgressUseCase,
    observeActiveProfile: ObserveActiveProfileUseCase,
    private val preferences: AmmiraTvPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(VodHomeUiState())
    val state: StateFlow<VodHomeUiState> = _state.asStateFlow()

    private val allItems = MutableStateFlow<List<VodItem>>(emptyList())

    /** Playlist the catalogue belongs to; the detail screen needs its id. */
    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    private val profileId = observeActiveProfile()
        .map { it?.id ?: 1L }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1L)

    val continueWatching: StateFlow<List<WatchProgress>> = profileId
        .flatMapLatest { observeContinueWatching(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** contentKey -> progress; drives the watched tick and the bar on posters. */
    val progressByKey: StateFlow<Map<String, WatchProgress>> = profileId
        .flatMapLatest { observeWatchProgress(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Items after category + text filter. Filtering runs off the main thread: lists can be 30k+. */
    val visibleItems: StateFlow<List<VodItem>> = combine(allItems, _state) { items, s ->
        val q = s.query.trim().lowercase(Locale.ROOT)
        items.filter { item ->
            val inCategory = when (s.selectedCategoryId) {
                null -> true
                PINNED_ITALIAN -> item.categoryId in s.italianCategoryIds
                else -> item.categoryId == s.selectedCategoryId
            }
            inCategory &&
                (q.isEmpty() || item.name.lowercase(Locale.ROOT).contains(q))
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            preferences.activePlaylistId.collect { id ->
                _playlist.value = if (id > 0) getPlaylistById(id) else null
                load()
            }
        }
    }

    fun setKind(kind: VodKind) {
        if (_state.value.kind == kind) return
        _state.update { it.copy(kind = kind, selectedCategoryId = null, query = "") }
        load()
    }

    fun selectCategory(id: String?) = _state.update { it.copy(selectedCategoryId = id) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun retry() = load()

    private fun load() {
        val playlist = _playlist.value
        if (playlist == null || playlist.type != PlaylistType.XTREAM) {
            allItems.value = emptyList()
            _state.update { it.copy(unsupported = true, isLoading = false, error = null, categories = emptyList()) }
            return
        }

        val kind = _state.value.kind
        _state.update { it.copy(unsupported = false, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val catalog = getVodCatalog(playlist, kind)
                allItems.value = catalog.items
                val italianIds = catalog.categories.filter { isItalianCategory(it.name) }.map { it.id }.toSet()
                _state.update {
                    it.copy(
                        isLoading = false,
                        categories = catalog.categories,
                        italianCategoryIds = italianIds,
                        // Open on the Italian folder when the provider has one
                        selectedCategoryId = if (italianIds.isNotEmpty()) PINNED_ITALIAN else null
                    )
                }
            } catch (e: Exception) {
                allItems.value = emptyList()
                _state.update {
                    it.copy(isLoading = false, error = "Impossibile caricare il catalogo: ${e.localizedMessage ?: "errore di rete"}")
                }
            }
        }
    }
}
