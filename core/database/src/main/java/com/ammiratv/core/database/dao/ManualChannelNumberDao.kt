package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ammiratv.core.database.entity.ManualChannelNumberEntity

@Dao
interface ManualChannelNumberDao {
    @Query("SELECT * FROM manual_channel_number")
    suspend fun getAll(): List<ManualChannelNumberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ManualChannelNumberEntity)

    @Query("DELETE FROM manual_channel_number WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM manual_channel_number")
    suspend fun deleteAll()
}
