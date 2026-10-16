package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ammiratv.core.database.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recording ORDER BY startTime DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recording WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recording WHERE status = 'SCHEDULED' ORDER BY startTime ASC")
    suspend fun getScheduled(): List<RecordingEntity>

    @Query("SELECT * FROM recording WHERE status = 'RECORDING'")
    suspend fun getRunning(): List<RecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Query("DELETE FROM recording WHERE id = :id")
    suspend fun delete(id: Long)
}
