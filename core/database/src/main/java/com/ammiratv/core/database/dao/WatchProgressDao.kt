package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ammiratv.core.database.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun get(profileId: Long, contentKey: String): WatchProgressEntity?

    /** Started but not finished, most recent first: the "Continua a guardare" row. */
    @Query(
        """
        SELECT * FROM watch_progress
        WHERE profileId = :profileId AND isWatched = 0 AND positionMs > 0
        ORDER BY updatedAt DESC LIMIT :limit
        """
    )
    fun observeContinueWatching(profileId: Long, limit: Int = 20): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId")
    fun observeAll(profileId: Long): Flow<List<WatchProgressEntity>>

    @Query("UPDATE watch_progress SET isWatched = :watched, positionMs = CASE WHEN :watched = 1 THEN 0 ELSE positionMs END WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun setWatched(profileId: Long, contentKey: String, watched: Boolean)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND contentKey = :contentKey")
    suspend fun delete(profileId: Long, contentKey: String)
}
