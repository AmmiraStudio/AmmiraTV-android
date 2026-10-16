package com.ammiratv.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.domain.usecase.UniversalSearchResult
import com.ammiratv.domain.usecase.UniversalSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val universalSearchUseCase: UniversalSearchUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Debounced so that typing with the on-screen keyboard (or a slow remote) doesn't fire a
     * database scan per keystroke; flatMapLatest drops results of superseded queries.
     */
    val results: StateFlow<UniversalSearchResult> = _query
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q -> flow { emit(universalSearchUseCase(q)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UniversalSearchResult())

    fun setQuery(value: String) {
        _query.value = value
    }
}
