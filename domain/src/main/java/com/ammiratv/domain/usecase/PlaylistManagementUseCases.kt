package com.ammiratv.domain.usecase

import com.ammiratv.core.common.Dispatcher
import com.ammiratv.core.common.AmmiraTvDispatchers
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.network.xtream.XtreamClient
import com.ammiratv.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetPlaylistsUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    operator fun invoke(): Flow<List<Playlist>> = playlistRepository.getPlaylists()
}

/** Deleting a playlist cascades (FK) to its categories, channels and favourites. */
class DeletePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long) = playlistRepository.deletePlaylist(playlistId)
}

/**
 * Re-reads expiry date and connection counters from the provider (Xtream only) and stores them.
 * This only authenticates: it never opens a stream, so it does not use up a connection slot.
 */
class RefreshProviderInfoUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val xtreamClient: XtreamClient,
    @Dispatcher(AmmiraTvDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    /** Returns false when the provider could not be reached; stored values are then left untouched. */
    suspend operator fun invoke(playlist: Playlist): Boolean {
        if (playlist.type != PlaylistType.XTREAM) return true
        val user = playlist.username ?: return false
        val pass = playlist.password ?: return false

        return withContext(ioDispatcher) {
            try {
                val info = xtreamClient.authenticate(playlist.url, user, pass).userInfo
                    ?: return@withContext false
                playlistRepository.updatePlaylist(
                    playlist.copy(
                        expirationDate = info.expirationTimestamp,
                        maxConnections = info.maxConnectionsCount,
                        activeConnections = info.activeConnectionsCount
                    )
                )
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
