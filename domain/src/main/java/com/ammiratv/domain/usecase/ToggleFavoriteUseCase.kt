package com.ammiratv.domain.usecase

import com.ammiratv.domain.repository.ChannelRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(profileId: Long, channelId: Long, currentFavoriteState: Boolean) {
        channelRepository.setFavorite(profileId, channelId, !currentFavoriteState)
    }
}
