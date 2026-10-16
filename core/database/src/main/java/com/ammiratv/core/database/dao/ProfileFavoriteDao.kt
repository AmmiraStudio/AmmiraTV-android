package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ammiratv.core.database.entity.ChannelEntity
import com.ammiratv.core.database.entity.ProfileFavoriteEntity
import kotlinx.coroutines.flow.Flow

/** A favourite expressed by stream URL, which survives a playlist re-import (channel ids don't). */
data class FavoriteUrlRow(
    val profileId: Long,
    val streamUrl: String
)

@Dao
interface ProfileFavoriteDao {

    @Query(
        """
        SELECT c.* FROM channel c
        INNER JOIN profile_favorite pf ON pf.channelId = c.id
        WHERE pf.profileId = :profileId AND c.isHidden = 0
        ORDER BY c.sortOrder ASC, c.id ASC
        """
    )
    fun getFavoriteChannels(profileId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT channelId FROM profile_favorite WHERE profileId = :profileId")
    fun getFavoriteIds(profileId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: ProfileFavoriteEntity)

    @Query("DELETE FROM profile_favorite WHERE profileId = :profileId AND channelId = :channelId")
    suspend fun remove(profileId: Long, channelId: Long)

    @Query(
        """
        SELECT pf.profileId AS profileId, c.streamUrl AS streamUrl
        FROM profile_favorite pf
        INNER JOIN channel c ON c.id = pf.channelId
        WHERE c.playlistId = :playlistId
        """
    )
    suspend fun getFavoriteUrls(playlistId: Long): List<FavoriteUrlRow>

    @Query(
        """
        INSERT OR IGNORE INTO profile_favorite (profileId, channelId)
        SELECT :profileId, id FROM channel WHERE playlistId = :playlistId AND streamUrl = :streamUrl
        """
    )
    suspend fun restore(profileId: Long, playlistId: Long, streamUrl: String)
}
