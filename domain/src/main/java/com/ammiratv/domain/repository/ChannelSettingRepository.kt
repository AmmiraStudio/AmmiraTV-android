package com.ammiratv.domain.repository

import com.ammiratv.core.model.ChannelSetting

interface ChannelSettingRepository {
    suspend fun get(streamUrl: String): ChannelSetting?

    /** An empty setting removes the row. */
    suspend fun save(setting: ChannelSetting)
}
