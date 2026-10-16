package com.ammiratv.domain.repository

import com.ammiratv.core.model.Channel
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for channel operations.
 */
interface ChannelRepository {
    fun getChannelsByCategory(categoryId: Long): Flow<List<Channel>>
    fun getChannelsByPlaylist(playlistId: Long): Flow<List<Channel>>

    /** Favourites are per profile. */
    fun getFavorites(profileId: Long): Flow<List<Channel>>
    fun getFavoriteIds(profileId: Long): Flow<Set<Long>>

    suspend fun getChannelById(id: Long): Channel?

    /** Channels sharing an EPG id, most reliable first. Used to map EPG programs back to channels. */
    suspend fun getChannelsByTvgId(tvgId: String): List<Channel>
    suspend fun searchChannels(query: String): List<Channel>
    suspend fun setFavorite(profileId: Long, channelId: Long, isFavorite: Boolean)
    suspend fun updateWatchProgress(channelId: Long, positionMs: Long)
    suspend fun saveChannels(channels: List<Channel>)
    suspend fun getChannelCount(playlistId: Long): Int
    suspend fun deleteChannelsByPlaylist(playlistId: Long)
    suspend fun findAlternatives(channel: Channel): List<Channel>
    suspend fun updateReliability(channelId: Long, delta: Int)

    /** Every live channel of a playlist, for batch jobs (EPG matching, stream tests). */
    suspend fun getLiveChannels(playlistId: Long): List<Channel>
    suspend fun setTvgId(channelId: Long, tvgId: String)

    /** Runs [block] on every live channel of the playlist, 1000 at a time (bounded memory). */
    suspend fun forEachLiveChannelPage(playlistId: Long, pageSize: Int = 1000, block: suspend (List<Channel>) -> Unit)

    suspend fun setTvgIds(pairs: List<Pair<Long, String>>)

    suspend fun getChannelsByIds(ids: Collection<Long>): List<Channel>

    /** (profileId, streamUrl) pairs, so favourites can be re-applied after a playlist re-import. */
    suspend fun snapshotFavorites(playlistId: Long): List<Pair<Long, String>>
    suspend fun restoreFavorites(playlistId: Long, favorites: List<Pair<Long, String>>)

    /** Replaces every TV-style number of [playlistId]; channels not in [numbers] end up unnumbered. */
    suspend fun replaceChannelNumbers(playlistId: Long, numbers: Map<Long, Int>)

    /** Live, visible channels carrying [number] in [playlistId] (several when quality variants exist). */
    suspend fun getChannelsByNumber(playlistId: Long, number: Int): List<Channel>
}
