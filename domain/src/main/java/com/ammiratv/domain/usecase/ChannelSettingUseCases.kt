package com.ammiratv.domain.usecase

import com.ammiratv.core.model.ChannelSetting
import com.ammiratv.domain.repository.ChannelSettingRepository
import javax.inject.Inject

class ChannelSettingUseCase @Inject constructor(
    private val repository: ChannelSettingRepository
) {
    suspend fun get(streamUrl: String): ChannelSetting? = repository.get(streamUrl)
    suspend fun save(setting: ChannelSetting) = repository.save(setting)
}
