package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ammiratv.core.database.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channel WHERE categoryId = :categoryId AND isHidden = 0 ORDER BY sortOrder ASC")
    fun getByCategory(categoryId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channel WHERE playlistId = :playlistId AND isHidden = 0 ORDER BY sortOrder ASC")
    fun getByPlaylist(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channel WHERE isFavorite = 1 ORDER BY sortOrder ASC")
    fun getFavorites(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channel WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channel WHERE cleanName LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' ORDER BY cleanName ASC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 50): List<ChannelEntity>

    @Query("SELECT * FROM channel WHERE tvgId = :tvgId AND isHidden = 0 ORDER BY reliability DESC, id ASC")
    suspend fun getByTvgId(tvgId: String): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Update
    suspend fun update(channel: ChannelEntity)

    @Query("UPDATE channel SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun setFavorite(channelId: Long, isFavorite: Boolean)

    @Query("UPDATE channel SET lastWatched = :timestamp, watchPosition = :position WHERE id = :channelId")
    suspend fun updateWatchProgress(channelId: Long, timestamp: Long, position: Long)

    @Query("DELETE FROM channel WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("""
        SELECT * FROM channel 
        WHERE id != :excludeId 
          AND isHidden = 0 
          AND ((tvgId IS NOT NULL AND tvgId != '' AND tvgId = :tvgId) 
               OR (LOWER(cleanName) = LOWER(:cleanName)))
        ORDER BY reliability DESC, id ASC
    """)
    suspend fun findAlternatives(excludeId: Long, tvgId: String?, cleanName: String): List<ChannelEntity>

    @Query("SELECT * FROM channel WHERE playlistId = :playlistId AND streamType = 0")
    suspend fun getLiveByPlaylist(playlistId: Long): List<ChannelEntity>

    @Query("UPDATE channel SET tvgId = :tvgId WHERE id = :channelId")
    suspend fun setTvgId(channelId: Long, tvgId: String)

    /** Live channels in id order, one page at a time, so batch jobs never hold a whole 50k playlist. */
    @Query("SELECT * FROM channel WHERE playlistId = :playlistId AND streamType = 0 AND id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getLivePage(playlistId: Long, afterId: Long, limit: Int): List<ChannelEntity>

    /** [ids] must stay under SQLite's 999-variable limit (older Android): callers chunk. */
    @Query("SELECT * FROM channel WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ChannelEntity>

    /** Many guide-id updates in ONE transaction instead of one disk sync per channel. */
    @androidx.room.Transaction
    suspend fun setTvgIds(pairs: List<Pair<Long, String>>) {
        for ((id, tvgId) in pairs) setTvgId(id, tvgId)
    }

    @Query("UPDATE channel SET reliability = reliability + :delta WHERE id = :channelId")
    suspend fun updateReliability(channelId: Long, delta: Int)

    @Query("SELECT COUNT(*) FROM channel WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    // ── TV-style channel numbers ──────────────────────────────────────────

    @Query("UPDATE channel SET channelNumber = NULL WHERE playlistId = :playlistId AND channelNumber IS NOT NULL")
    suspend fun clearChannelNumbers(playlistId: Long)

    @Query("UPDATE channel SET channelNumber = :number WHERE id = :channelId")
    suspend fun setChannelNumber(channelId: Long, number: Int)

    /** Replaces every number of a playlist in one transaction, so readers never see a half update. */
    @androidx.room.Transaction
    suspend fun replaceChannelNumbers(playlistId: Long, numbers: Map<Long, Int>) {
        clearChannelNumbers(playlistId)
        for ((id, number) in numbers) setChannelNumber(id, number)
    }

    @Query("""
        SELECT * FROM channel
        WHERE playlistId = :playlistId AND channelNumber = :number AND streamType = 0 AND isHidden = 0
    """)
    suspend fun getByNumber(playlistId: Long, number: Int): List<ChannelEntity>
}
