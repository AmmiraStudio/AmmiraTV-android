package com.ammiratv.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.ammiratv.core.database.dao.CategoryDao
import com.ammiratv.core.database.dao.ChannelDao
import com.ammiratv.core.database.dao.ChannelSettingDao
import com.ammiratv.core.database.dao.EpgProgramDao
import com.ammiratv.core.database.dao.PlaybackEventDao
import com.ammiratv.core.database.dao.ManualChannelNumberDao
import com.ammiratv.core.database.dao.PlaylistDao
import com.ammiratv.core.database.entity.PlaybackEventEntity
import com.ammiratv.core.database.dao.ProfileDao
import com.ammiratv.core.database.dao.ProfileFavoriteDao
import com.ammiratv.core.database.dao.RecordingDao
import com.ammiratv.core.database.dao.WatchProgressDao
import com.ammiratv.core.database.entity.CategoryEntity
import com.ammiratv.core.database.entity.ChannelEntity
import com.ammiratv.core.database.entity.ChannelSettingEntity
import com.ammiratv.core.database.entity.EpgProgramEntity
import com.ammiratv.core.database.entity.EpgSourceEntity
import com.ammiratv.core.database.entity.ManualChannelNumberEntity
import com.ammiratv.core.database.entity.PlaylistEntity
import com.ammiratv.core.database.entity.ProfileEntity
import com.ammiratv.core.database.entity.ProfileFavoriteEntity
import com.ammiratv.core.database.entity.RecordingEntity
import com.ammiratv.core.database.entity.WatchProgressEntity

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        EpgSourceEntity::class,
        EpgProgramEntity::class,
        ProfileEntity::class,
        ProfileFavoriteEntity::class,
        WatchProgressEntity::class,
        ChannelSettingEntity::class,
        RecordingEntity::class,
        PlaybackEventEntity::class,
        ManualChannelNumberEntity::class
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // v2: watch progress, per-channel settings, recordings (new tables only)
        AutoMigration(from = 1, to = 2),
        // v3: playback events for the provider report card (new table only)
        AutoMigration(from = 2, to = 3),
        // v4: TV-style channel numbers (new nullable column + index, new table)
        AutoMigration(from = 3, to = 4),
        // v5: alternate servers (DNS) of a playlist (new nullable column)
        AutoMigration(from = 4, to = 5)
    ]
)
abstract class AmmiraTvDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun epgProgramDao(): EpgProgramDao
    abstract fun profileDao(): ProfileDao
    abstract fun profileFavoriteDao(): ProfileFavoriteDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun channelSettingDao(): ChannelSettingDao
    abstract fun recordingDao(): RecordingDao
    abstract fun playbackEventDao(): PlaybackEventDao
    abstract fun manualChannelNumberDao(): ManualChannelNumberDao
}
