package com.ammiratv.core.database.di

import android.content.Context
import androidx.room.Room
import com.ammiratv.core.database.AmmiraTvDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AmmiraTvDatabase {
        return Room.databaseBuilder(
            context,
            AmmiraTvDatabase::class.java,
            "ammiratv.db"
        )
            // Upgrades go through AutoMigration; only a downgrade (older app build over a newer
            // database) may reset the data. Never wipe user favourites/progress on a missing migration.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun providePlaylistDao(db: AmmiraTvDatabase) = db.playlistDao()

    @Provides
    fun provideCategoryDao(db: AmmiraTvDatabase) = db.categoryDao()

    @Provides
    fun provideChannelDao(db: AmmiraTvDatabase) = db.channelDao()

    @Provides
    fun provideEpgProgramDao(db: AmmiraTvDatabase) = db.epgProgramDao()

    @Provides
    fun provideProfileDao(db: AmmiraTvDatabase) = db.profileDao()

    @Provides
    fun provideProfileFavoriteDao(db: AmmiraTvDatabase) = db.profileFavoriteDao()

    @Provides
    fun provideWatchProgressDao(db: AmmiraTvDatabase) = db.watchProgressDao()

    @Provides
    fun provideChannelSettingDao(db: AmmiraTvDatabase) = db.channelSettingDao()

    @Provides
    fun provideRecordingDao(db: AmmiraTvDatabase) = db.recordingDao()

    @Provides
    fun providePlaybackEventDao(db: AmmiraTvDatabase) = db.playbackEventDao()

    @Provides
    fun provideManualChannelNumberDao(db: AmmiraTvDatabase) = db.manualChannelNumberDao()
}
