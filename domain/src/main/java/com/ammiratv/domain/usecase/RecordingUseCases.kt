package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.ConnectionBudget
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.core.model.Recording
import com.ammiratv.core.model.RecordingStatus
import com.ammiratv.domain.repository.PlaylistRepository
import com.ammiratv.domain.repository.RecordingEngine
import com.ammiratv.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

sealed interface RecordingOutcome {
    data class Scheduled(val recordingId: Long, val startsNow: Boolean) : RecordingOutcome

    /** The provider's connection limit leaves no room for a second stream. */
    data class NoFreeConnection(val maxConnections: Int) : RecordingOutcome
    data class Rejected(val reason: String) : RecordingOutcome
}

class ObserveRecordingsUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    operator fun invoke(): Flow<List<Recording>> = repository.observeAll()
}

class ManageRecordingsUseCase @Inject constructor(
    private val engine: RecordingEngine,
    private val repository: RecordingRepository
) {
    suspend fun stop(id: Long) = engine.stop(id)
    suspend fun delete(id: Long) = engine.delete(id)
    suspend fun get(id: Long) = repository.getById(id)
}

/**
 * Records [channel] either right now (when no program is given) or for a guide entry.
 * A recording is an extra stream: the connection limit is checked first.
 */
class StartRecordingUseCase @Inject constructor(
    private val engine: RecordingEngine,
    private val playlistRepository: PlaylistRepository,
    private val refreshProviderInfo: RefreshProviderInfoUseCase
) {
    /**
     * @param watchingLive true when the live player currently holds a stream on the same account
     */
    suspend operator fun invoke(
        channel: Channel,
        program: EpgProgram?,
        watchingLive: Boolean,
        defaultDurationMs: Long = DEFAULT_DURATION_MS
    ): RecordingOutcome {
        val now = System.currentTimeMillis()
        val start = program?.startTime?.coerceAtLeast(now) ?: now
        val end = program?.endTime ?: (now + defaultDurationMs)
        if (end <= now) return RecordingOutcome.Rejected("Il programma è già terminato")

        // Only the part that overlaps live viewing needs a free slot; a future recording
        // while watching something else still competes at its start time, so check regardless.
        playlistRepository.getPlaylistById(channel.playlistId)?.let { playlist ->
            refreshProviderInfo(playlist) // updates the active-connection counter (auth only)
            val fresh = playlistRepository.getPlaylistById(channel.playlistId) ?: playlist
            val ok = ConnectionBudget.canOpenAnother(
                maxConnections = fresh.maxConnections,
                providerActive = fresh.activeConnections,
                inUse = if (watchingLive) 1 else 0
            )
            if (!ok) return RecordingOutcome.NoFreeConnection(fresh.maxConnections ?: 1)
        }

        val id = engine.schedule(
            Recording(
                channelName = channel.cleanName,
                title = program?.title ?: channel.cleanName,
                streamUrl = channel.streamUrl,
                startTime = start,
                endTime = end,
                status = RecordingStatus.SCHEDULED
            )
        )
        return RecordingOutcome.Scheduled(id, startsNow = start <= now + 1_000)
    }

    private companion object {
        const val DEFAULT_DURATION_MS = 60 * 60 * 1000L
    }
}
