package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ammiratv.core.database.entity.PlaybackEventEntity

@Dao
interface PlaybackEventDao {

    @Insert
    suspend fun insert(event: PlaybackEventEntity)

    @Query("SELECT * FROM playback_event WHERE playlistId = :playlistId AND timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getSince(playlistId: Long, since: Long): List<PlaybackEventEntity>

    /** Keeps the table small: the report only looks at the last weeks. */
    @Query("DELETE FROM playback_event WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
