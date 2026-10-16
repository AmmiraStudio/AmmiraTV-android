package com.ammiratv.domain.repository

import com.ammiratv.core.model.Playlist
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for playlist operations.
 * Implementation lives in the :data module.
 */
interface PlaylistRepository {
    fun getPlaylists(): Flow<List<Playlist>>
    fun getActivePlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: Long): Playlist?
    suspend fun addPlaylist(playlist: Playlist): Long
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(id: Long)
}
