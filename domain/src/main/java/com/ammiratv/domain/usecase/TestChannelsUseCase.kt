package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.ConnectionBudget
import com.ammiratv.core.model.DuplicateMerger
import com.ammiratv.domain.repository.ChannelRepository
import com.ammiratv.domain.repository.PlaylistRepository
import com.ammiratv.domain.repository.StreamProbe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

sealed interface ChannelTestProgress {
    data class Running(val tested: Int, val total: Int, val alive: Int) : ChannelTestProgress
    data class Finished(val tested: Int, val alive: Int) : ChannelTestProgress

    /** Test refused up front, with the reason shown to the user. */
    data class Refused(val reason: String) : ChannelTestProgress
}

/**
 * Smart Channel Cleaner, stream test: checks which copies of duplicated channels actually
 * work and records the result in their reliability, so failover and the merged list prefer
 * the good ones.
 *
 * Deliberately gentle: one stream at a time, only a few bytes each, only channels that have
 * a duplicate (those are the ones where the answer changes what the app does), and never
 * when the provider's connection limit has no spare slot.
 */
class TestChannelsUseCase @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository,
    private val probe: StreamProbe
) {
    operator fun invoke(playlistId: Long, isWatchingLive: Boolean, maxToTest: Int = 150): Flow<ChannelTestProgress> = flow {
        val playlist = playlistRepository.getPlaylistById(playlistId)
        if (playlist == null) {
            emit(ChannelTestProgress.Refused("Nessuna sorgente attiva"))
            return@flow
        }
        if (!ConnectionBudget.canOpenAnother(
                playlist.maxConnections, playlist.activeConnections, if (isWatchingLive) 1 else 0
            )
        ) {
            emit(ChannelTestProgress.Refused("Nessuna connessione libera: il test avrebbe disturbato la visione"))
            return@flow
        }

        // Only channels that exist more than once are worth probing
        val groups = channelRepository.getLiveChannels(playlistId).groupBy { DuplicateMerger.keyOf(it) }
        val candidates: List<Channel> = groups.values.filter { it.size > 1 }.flatten().take(maxToTest)

        var alive = 0
        candidates.forEachIndexed { index, channel ->
            val ok = probe.isAlive(channel.streamUrl)
            channelRepository.updateReliability(channel.id, if (ok) +1 else -3)
            if (ok) alive++
            emit(ChannelTestProgress.Running(index + 1, candidates.size, alive))
        }
        emit(ChannelTestProgress.Finished(candidates.size, alive))
    }
}
