package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ammiratv.core.database.entity.ChannelSettingEntity

@Dao
interface ChannelSettingDao {

    @Query("SELECT * FROM channel_setting WHERE streamUrl = :streamUrl")
    suspend fun get(streamUrl: String): ChannelSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: ChannelSettingEntity)

    @Query("DELETE FROM channel_setting WHERE streamUrl = :streamUrl")
    suspend fun delete(streamUrl: String)
}
