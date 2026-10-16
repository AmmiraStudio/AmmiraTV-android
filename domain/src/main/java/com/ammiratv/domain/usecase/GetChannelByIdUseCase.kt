package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Channel
import com.ammiratv.domain.repository.ChannelRepository
import javax.inject.Inject

class GetChannelByIdUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(id: Long): Channel? = channelRepository.getChannelById(id)
}
