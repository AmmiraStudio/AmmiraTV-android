package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.ChannelSettingDao
import com.ammiratv.core.database.entity.ChannelSettingEntity
import com.ammiratv.core.model.AspectRatio
import com.ammiratv.core.model.ChannelSetting
import com.ammiratv.domain.repository.ChannelSettingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelSettingRepositoryImpl @Inject constructor(
    private val dao: ChannelSettingDao
) : ChannelSettingRepository {

    override suspend fun get(streamUrl: String): ChannelSetting? =
        dao.get(streamUrl)?.let {
            ChannelSetting(
                streamUrl = it.streamUrl,
                aspectRatio = it.aspectRatio?.let { name -> runCatching { AspectRatio.valueOf(name) }.getOrNull() },
                audioLanguage = it.audioLanguage,
                subtitleLanguage = it.subtitleLanguage
            )
        }

    override suspend fun save(setting: ChannelSetting) {
        if (setting.isEmpty) {
            dao.delete(setting.streamUrl)
        } else {
            dao.upsert(
                ChannelSettingEntity(
                    streamUrl = setting.streamUrl,
                    aspectRatio = setting.aspectRatio?.name,
                    audioLanguage = setting.audioLanguage,
                    subtitleLanguage = setting.subtitleLanguage
                )
            )
        }
    }
}
