package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Channel
import com.ammiratv.domain.repository.ChannelRepository
import javax.inject.Inject

/**
 * Finds alternative streams/channels for failover when a stream fails or drops.
 * Matches by tvgId or normalized clean channel name across all playlists.
 */
class FindAlternativeStreamUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(channel: Channel): List<Channel> {
        return channelRepository.findAlternatives(channel)
    }

    suspend fun recordStreamSuccess(channelId: Long) {
        channelRepository.updateReliability(channelId, delta = 1)
    }

    suspend fun recordStreamFailure(channelId: Long) {
        channelRepository.updateReliability(channelId, delta = -2)
    }
}
