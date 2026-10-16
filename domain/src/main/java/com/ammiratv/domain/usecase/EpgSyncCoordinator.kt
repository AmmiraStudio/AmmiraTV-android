package com.ammiratv.domain.usecase

import com.ammiratv.core.common.ApplicationScope
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.domain.repository.EpgRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EpgSyncState {
    data object Idle : EpgSyncState
    data class Syncing(val programs: Int) : EpgSyncState
    data class Done(val programs: Int) : EpgSyncState
    data class Failed(val message: String) : EpgSyncState
}

/**
 * Runs XMLTV downloads on the application scope so they survive screen changes,
 * one at a time (EPG files can be hundreds of MB: never download two in parallel).
 */
@Singleton
class EpgSyncCoordinator @Inject constructor(
    private val importXmlTv: ImportXmlTvEpgUseCase,
    private val autoMatchEpg: AutoMatchEpgUseCase,
    private val epgRepository: EpgRepository,
    private val playlistRepository: com.ammiratv.domain.repository.PlaylistRepository,
    private val xtreamClient: com.ammiratv.core.network.xtream.XtreamClient,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<EpgSyncState>(EpgSyncState.Idle)
    val state: StateFlow<EpgSyncState> = _state.asStateFlow()

    private val lock = Any()
    private val queue = ArrayDeque<String>()
    private var worker: Job? = null

    /** Queues [url]; ignored when blank or already queued. */
    fun sync(url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        synchronized(lock) {
            if (clean !in queue) queue.addLast(clean)
            // `worker` is reset to null under this same lock when the queue runs dry,
            // so an item queued right as the worker finishes is never lost.
            if (worker == null) worker = scope.launch { drain() }
        }
    }

    /** Re-syncs every known source whose data is older than [maxAgeMs]. Call on app start. */
    fun syncStale(maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {
        scope.launch {
            val now = System.currentTimeMillis()
            val sources = epgRepository.getSources()
            sources
                .filter { it.url.startsWith("http", ignoreCase = true) && now - it.lastUpdated > maxAgeMs }
                .forEach { sync(it.url) }

            // A source row is only created by a successful download. If the first sync after
            // adding an Xtream account failed (or the app was closed meanwhile) there is no row
            // to retry, so the guide would stay empty forever: derive the URL from the account.
            val known = sources.map { it.url }.toSet()
            runCatching { playlistRepository.getPlaylists().first() }.getOrDefault(emptyList())
                .filter { it.isActive && it.type == PlaylistType.XTREAM }
                .filter { !it.username.isNullOrBlank() && !it.password.isNullOrBlank() }
                .map { xtreamClient.buildXmltvUrl(it.url, it.username!!, it.password!!) }
                .filter { it !in known }
                .forEach { sync(it) }
        }
    }

    private suspend fun drain() {
        while (true) {
            val next = synchronized(lock) {
                queue.removeFirstOrNull().also { if (it == null) worker = null }
            } ?: return

            importXmlTv(next).collect { progress ->
                _state.value = when (progress) {
                    is ImportProgress.Loading -> EpgSyncState.Syncing(0)
                    is ImportProgress.Parsing -> EpgSyncState.Syncing(progress.count)
                    is ImportProgress.Success -> EpgSyncState.Done(progress.totalChannels)
                    is ImportProgress.Error -> EpgSyncState.Failed(progress.message)
                }
            }

            // Guide data just changed: give a guide to channels whose tvg-id doesn't match it
            if (_state.value is EpgSyncState.Done) {
                runCatching { autoMatchEpg() }
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_AGE_MS = 12 * 3600 * 1000L
    }
}
