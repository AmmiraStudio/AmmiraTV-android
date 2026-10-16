package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.PlaylistDao
import com.ammiratv.core.database.entity.PlaylistEntity
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : PlaylistRepository {

    override fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getActivePlaylists(): Flow<List<Playlist>> {
        return playlistDao.getActive().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getPlaylistById(id: Long): Playlist? {
        return playlistDao.getById(id)?.toDomain()
    }

    override suspend fun addPlaylist(playlist: Playlist): Long {
        return playlistDao.insert(playlist.toEntity())
    }

    override suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.update(playlist.toEntity())
    }

    override suspend fun deletePlaylist(id: Long) {
        playlistDao.deleteById(id)
    }
}

// ── Mappers ──────────────────────────────────────────────────────────────

private fun PlaylistEntity.toDomain() = Playlist(
    id = id,
    name = name,
    url = url,
    type = PlaylistType.entries[type],
    username = username,
    password = password,
    lastUpdated = lastUpdated,
    expirationDate = expirationDate,
    maxConnections = maxConnections,
    activeConnections = activeConnections,
    isActive = isActive,
    sortOrder = sortOrder,
    alternateUrls = alternateUrls?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
)

private fun Playlist.toEntity() = PlaylistEntity(
    id = id,
    name = name,
    url = url,
    type = type.ordinal,
    username = username,
    password = password,
    lastUpdated = lastUpdated,
    expirationDate = expirationDate,
    maxConnections = maxConnections,
    activeConnections = activeConnections,
    isActive = isActive,
    sortOrder = sortOrder,
    alternateUrls = alternateUrls.takeIf { it.isNotEmpty() }?.joinToString("\n")
)
