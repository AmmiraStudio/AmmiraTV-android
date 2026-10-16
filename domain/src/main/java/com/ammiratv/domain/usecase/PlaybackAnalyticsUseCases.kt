package com.ammiratv.domain.usecase

import com.ammiratv.core.model.BufferAdvisor
import com.ammiratv.core.model.PlaybackEvent
import com.ammiratv.core.model.ProviderReport
import com.ammiratv.core.model.ProviderReportCalculator
import com.ammiratv.domain.repository.PlaybackEventRepository
import javax.inject.Inject

private const val DAY_MS = 24 * 3600 * 1000L

class RecordPlaybackEventUseCase @Inject constructor(
    private val repository: PlaybackEventRepository
) {
    suspend operator fun invoke(event: PlaybackEvent) = repository.record(event)

    /** Drops events older than 30 days; called once per app start. */
    suspend fun prune() = repository.pruneOlderThan(System.currentTimeMillis() - 30 * DAY_MS)
}

class GetProviderReportUseCase @Inject constructor(
    private val repository: PlaybackEventRepository
) {
    suspend operator fun invoke(playlistId: Long, days: Int = 7): ProviderReport {
        val since = System.currentTimeMillis() - days * DAY_MS
        return ProviderReportCalculator.calculate(repository.eventsSince(playlistId, since))
    }

    /** Problems (failed starts, rebuffers, stalls) in the last [hours]; input of the verdict. */
    suspend fun recentProblems(playlistId: Long, hours: Int = 1): Int {
        val since = System.currentTimeMillis() - hours * 3600 * 1000L
        return repository.eventsSince(playlistId, since)
            .count { it.type != com.ammiratv.core.model.PlaybackEventType.START_OK }
    }
}

/** "Buffer che impara": proposes a bigger buffer shortly before the hours that usually go wrong. */
class RecommendBufferUseCase @Inject constructor(
    private val repository: PlaybackEventRepository
) {
    suspend operator fun invoke(playlistId: Long, currentBufferMs: Int): Int {
        val since = System.currentTimeMillis() - 14 * DAY_MS
        return BufferAdvisor.recommendedBufferMs(
            events = repository.eventsSince(playlistId, since),
            nowMs = System.currentTimeMillis(),
            currentBufferMs = currentBufferMs
        )
    }
}
