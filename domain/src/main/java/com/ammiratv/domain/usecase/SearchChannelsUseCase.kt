package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Channel
import com.ammiratv.domain.repository.ChannelRepository
import javax.inject.Inject

class SearchChannelsUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(query: String): List<Channel> {
        if (query.isBlank()) return emptyList()
        return channelRepository.searchChannels(query.trim())
    }
}
