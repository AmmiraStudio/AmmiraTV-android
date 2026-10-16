package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import javax.inject.Inject

/**
 * Re-imports a playlist in place and re-applies every profile's favourites afterwards.
 * Favourites are matched by stream URL, since channel ids change on re-import.
 */
class RefreshPlaylistUseCase @Inject constructor(
    private val importM3u: ImportM3uPlaylistUseCase,
    private val importXtream: ImportXtreamPlaylistUseCase,
    private val channelRepository: ChannelRepository
) {
    operator fun invoke(playlist: Playlist): Flow<ImportProgress> = flow {
        val source: Flow<ImportProgress> = when (playlist.type) {
            PlaylistType.M3U -> {
                if (!playlist.url.startsWith("http", ignoreCase = true)) {
                    emit(ImportProgress.Error("Questa playlist è stata caricata da file: non può essere aggiornata"))
                    return@flow
                }
                importM3u(
                    playlist.name,
                    (listOf(playlist.url) + playlist.alternateUrls).joinToString("\n"),
                    replacePlaylistId = playlist.id
                )
            }
            PlaylistType.XTREAM -> importXtream(
                playlistName = playlist.name,
                // Every known server: the best one is picked again, so a dead DNS no longer breaks the refresh
                serverUrls = (listOf(playlist.url) + playlist.alternateUrls).joinToString("\n"),
                username = playlist.username.orEmpty(),
                password = playlist.password.orEmpty(),
                replacePlaylistId = playlist.id
            )
            PlaylistType.STALKER -> {
                emit(ImportProgress.Error("Stalker/MAG non ancora supportato"))
                return@flow
            }
        }

        // Taken before the import deletes the channels the favourites point to.
        val favorites = channelRepository.snapshotFavorites(playlist.id)

        source.collect { progress ->
            if (progress is ImportProgress.Success) {
                channelRepository.restoreFavorites(playlist.id, favorites)
            }
            emit(progress)
        }
    }
}
