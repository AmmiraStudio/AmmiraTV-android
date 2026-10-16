package com.ammiratv.data.di

import com.ammiratv.data.repository.CategoryRepositoryImpl
import com.ammiratv.data.repository.ChannelRepositoryImpl
import com.ammiratv.data.repository.EpgRepositoryImpl
import com.ammiratv.data.repository.PlaylistRepositoryImpl
import com.ammiratv.data.repository.ProfileRepositoryImpl
import com.ammiratv.data.recording.RecordingManager
import com.ammiratv.data.repository.ChannelSettingRepositoryImpl
import com.ammiratv.data.repository.HttpStreamProbe
import com.ammiratv.domain.repository.StreamProbe
import com.ammiratv.domain.repository.ChannelSettingRepository
import com.ammiratv.data.repository.PlaybackEventRepositoryImpl
import com.ammiratv.domain.repository.PlaybackEventRepository
import com.ammiratv.data.repository.RecordingRepositoryImpl
import com.ammiratv.domain.repository.RecordingEngine
import com.ammiratv.domain.repository.RecordingRepository
import com.ammiratv.data.repository.VodRepositoryImpl
import com.ammiratv.data.repository.WatchProgressRepositoryImpl
import com.ammiratv.domain.repository.VodRepository
import com.ammiratv.domain.repository.WatchProgressRepository
import com.ammiratv.domain.repository.ProfileRepository
import com.ammiratv.domain.repository.CategoryRepository
import com.ammiratv.domain.repository.ChannelRepository
import com.ammiratv.domain.repository.EpgRepository
import com.ammiratv.domain.repository.PlaylistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: com.ammiratv.data.reminder.AlarmReminderScheduler):
        com.ammiratv.domain.repository.ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository

    @Binds
    @Singleton
    abstract fun bindEpgRepository(impl: EpgRepositoryImpl): EpgRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindStreamProbe(impl: HttpStreamProbe): StreamProbe

    @Binds
    @Singleton
    abstract fun bindChannelSettingRepository(impl: ChannelSettingRepositoryImpl): ChannelSettingRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackEventRepository(impl: PlaybackEventRepositoryImpl): PlaybackEventRepository

    @Binds
    @Singleton
    abstract fun bindRecordingRepository(impl: RecordingRepositoryImpl): RecordingRepository

    @Binds
    @Singleton
    abstract fun bindRecordingEngine(impl: RecordingManager): RecordingEngine

    @Binds
    @Singleton
    abstract fun bindVodRepository(impl: VodRepositoryImpl): VodRepository

    @Binds
    @Singleton
    abstract fun bindWatchProgressRepository(impl: WatchProgressRepositoryImpl): WatchProgressRepository
}
