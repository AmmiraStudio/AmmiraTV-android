package com.ammiratv.domain.usecase

import com.ammiratv.core.common.ChannelNameCleaner
import com.ammiratv.core.common.Dispatcher
import com.ammiratv.core.common.AmmiraTvDispatchers
import com.ammiratv.core.model.Category
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.ServerList
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.model.StreamType
import com.ammiratv.core.network.xtream.XtreamClient
import com.ammiratv.domain.repository.CategoryRepository
import com.ammiratv.domain.repository.ChannelRepository
import com.ammiratv.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ImportXtreamPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val categoryRepository: CategoryRepository,
    private val channelRepository: ChannelRepository,
    private val xtreamClient: XtreamClient,
    private val serverSelector: ServerSelector,
    @Dispatcher(AmmiraTvDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {

    operator fun invoke(
        playlistName: String,
        /** One address, or several (DNS / mirrors) separated by space, comma or new line. */
        serverUrls: String,
        username: String,
        password: String,
        /** When set, this existing playlist is refreshed in place instead of creating a new one. */
        replacePlaylistId: Long? = null
    ): Flow<ImportProgress> = flow {
        emit(ImportProgress.Loading("Connecting to Xtream server..."))

        // With several addresses, all are tried together and the fastest working one is used
        val candidates = ServerList.parse(serverUrls).ifEmpty { listOf(serverUrls.trim()) }
        var alternates: List<String> = emptyList()
        val serverUrl: String
        if (candidates.size > 1) {
            emit(ImportProgress.Loading("Verifico ${candidates.size} server e scelgo il migliore..."))
            val pick = serverSelector.pickXtream(candidates, username, password)
            if (pick == null) {
                emit(ImportProgress.Error("Nessuno dei ${candidates.size} server ha risposto: controlla indirizzi, utente e password"))
                return@flow
            }
            serverUrl = pick.best
            alternates = pick.others
        } else {
            serverUrl = candidates.first()
        }

        try {
            // 1. Authenticate with Xtream server
            val authResponse = xtreamClient.authenticate(serverUrl, username, password)
            val userInfo = authResponse.userInfo ?: run {
                emit(ImportProgress.Error("Authentication failed: invalid server response"))
                return@flow
            }

            if (userInfo.status.isNotEmpty() && !userInfo.status.equals("Active", ignoreCase = true)) {
                emit(ImportProgress.Error("Account is not active (status: ${userInfo.status})"))
                return@flow
            }

            // 2. Create (or reset) the Playlist record. Auth already succeeded, so a failed
            // login can never wipe the channels of the playlist being refreshed.
            val existing = replacePlaylistId?.let { playlistRepository.getPlaylistById(it) }
            val playlistId: Long
            if (existing != null) {
                playlistId = existing.id
                channelRepository.deleteChannelsByPlaylist(playlistId)
                categoryRepository.deleteCategoriesByPlaylist(playlistId)
                playlistRepository.updatePlaylist(
                    existing.copy(
                        url = serverUrl.trim(),
                        alternateUrls = alternates,
                        lastUpdated = System.currentTimeMillis(),
                        expirationDate = userInfo.expirationTimestamp,
                        maxConnections = userInfo.maxConnectionsCount,
                        activeConnections = userInfo.activeConnectionsCount
                    )
                )
            } else {
                playlistId = playlistRepository.addPlaylist(
                    Playlist(
                        name = playlistName.trim().ifEmpty { "Xtream Account" },
                        url = serverUrl.trim(),
                        alternateUrls = alternates,
                        type = PlaylistType.XTREAM,
                        username = username.trim(),
                        password = password.trim(),
                        lastUpdated = System.currentTimeMillis(),
                        expirationDate = userInfo.expirationTimestamp,
                        maxConnections = userInfo.maxConnectionsCount,
                        activeConnections = userInfo.activeConnectionsCount
                    )
                )
            }

            emit(ImportProgress.Loading("Fetching channel categories..."))

            // 3. Persist categories. Room generates the ids: the server's category_id is only
            // unique per account, so using it as primary key collides across playlists.
            val xtreamCategories = xtreamClient.getLiveCategories(serverUrl, username, password)
            val categoryIdByServerId = mutableMapOf<String, Long>()
            if (xtreamCategories.isNotEmpty()) {
                val ids = categoryRepository.saveCategories(
                    xtreamCategories.mapIndexed { index, xCat ->
                        Category(
                            playlistId = playlistId,
                            name = xCat.categoryName,
                            type = StreamType.LIVE,
                            sortOrder = index
                        )
                    }
                )
                xtreamCategories.zip(ids).forEach { (xCat, id) -> categoryIdByServerId[xCat.categoryId] = id }
            }
            val categoryNameById = xtreamCategories.associate { it.categoryId to it.categoryName }

            emit(ImportProgress.Loading("Fetching live stream channels..."))


            // Streams whose category the server never declared go to a fallback category,
            // otherwise the channel FK would reject them.
            var fallbackCategoryId: Long? = null
            suspend fun fallbackCategory(): Long =
                fallbackCategoryId ?: categoryRepository.saveCategories(
                    listOf(
                        Category(
                            playlistId = playlistId,
                            name = "Altro",
                            type = StreamType.LIVE,
                            sortOrder = Int.MAX_VALUE
                        )
                    )
                ).first().also { fallbackCategoryId = it }

            var totalCount = 0
            // 4. Live streams are read as a stream and saved 500 at a time: memory stays flat
            xtreamClient.streamLiveStreams(serverUrl, username, password, 500) { chunk ->
                val channels = chunk.map { stream ->
                    val streamUrl = xtreamClient.buildLiveStreamUrl(
                        serverUrl = serverUrl,
                        user = username,
                        pass = password,
                        streamId = stream.streamId
                    )
                    Channel(
                        playlistId = playlistId,
                        categoryId = categoryIdByServerId[stream.categoryId] ?: fallbackCategory(),
                        name = stream.name,
                        cleanName = ChannelNameCleaner.clean(stream.name),
                        streamUrl = streamUrl,
                        logoUrl = stream.streamIcon?.takeIf { it.isNotBlank() },
                        streamType = StreamType.LIVE,
                        tvgId = stream.epgChannelId?.takeIf { it.isNotBlank() },
                        groupTitle = categoryNameById[stream.categoryId],
                        sortOrder = stream.num
                    )
                }

                channelRepository.saveChannels(channels)
                totalCount += channels.size
                emit(ImportProgress.Parsing(totalCount))
            }

            val epgUrl = xtreamClient.buildXmltvUrl(serverUrl, username, password)
            emit(ImportProgress.Success(playlistId, totalCount, epgUrl))

        } catch (e: OutOfMemoryError) {
            emit(ImportProgress.Error("Memoria insufficiente: la playlist e' troppo grande per questo dispositivo"))
        } catch (e: Exception) {
            emit(ImportProgress.Error("Xtream import failed: ${e.localizedMessage ?: e.message}"))
        }
    }.flowOn(ioDispatcher)
}
