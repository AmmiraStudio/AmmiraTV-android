package com.ammiratv.domain.usecase

import com.ammiratv.core.common.Dispatcher
import com.ammiratv.core.common.AmmiraTvDispatchers
import com.ammiratv.core.model.Category
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.ServerList
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.model.StreamType
import com.ammiratv.core.network.m3u.M3uDownloader
import com.ammiratv.core.network.m3u.M3uParser
import com.ammiratv.domain.repository.CategoryRepository
import com.ammiratv.domain.repository.ChannelRepository
import com.ammiratv.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class ImportM3uPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val categoryRepository: CategoryRepository,
    private val channelRepository: ChannelRepository,
    private val m3uDownloader: M3uDownloader,
    private val serverSelector: ServerSelector,
    @Dispatcher(AmmiraTvDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {

    operator fun invoke(
        playlistName: String,
        source: String,
        isRawContent: Boolean = false,
        /** When set, this existing playlist is refreshed in place instead of creating a new one. */
        replacePlaylistId: Long? = null
    ): Flow<ImportProgress> = flow {
        emit(ImportProgress.Loading("Preparing M3U playlist import..."))

        // Several addresses of the same list: the fastest one that really serves a playlist wins
        var effectiveSource = source
        var alternates: List<String> = emptyList()
        if (!isRawContent) {
            val candidates = ServerList.parse(source)
            if (candidates.size > 1) {
                emit(ImportProgress.Loading("Verifico ${candidates.size} indirizzi e scelgo il migliore..."))
                val pick = serverSelector.pickM3u(candidates)
                if (pick == null) {
                    emit(ImportProgress.Error("Nessuno dei ${candidates.size} indirizzi ha restituito una playlist"))
                    return@flow
                }
                effectiveSource = pick.best
                alternates = pick.others
            }
        }

        try {
            // 1. Create (or reset) the Playlist entry
            val existing = replacePlaylistId?.let { playlistRepository.getPlaylistById(it) }
            val playlistId: Long
            if (existing != null) {
                playlistId = existing.id
                // Categories cascade to channels; callers snapshot favourites beforehand.
                channelRepository.deleteChannelsByPlaylist(playlistId)
                categoryRepository.deleteCategoriesByPlaylist(playlistId)
                playlistRepository.updatePlaylist(
                    existing.copy(
                        url = if (isRawContent) existing.url else effectiveSource.trim(),
                        alternateUrls = alternates,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            } else {
                playlistId = playlistRepository.addPlaylist(
                    Playlist(
                        name = playlistName.trim().ifEmpty { "M3U Playlist" },
                        url = if (isRawContent) "local" else effectiveSource.trim(),
                        alternateUrls = alternates,
                        type = PlaylistType.M3U,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }

            emit(ImportProgress.Loading("Fetching playlist stream..."))

            val reader: BufferedReader = if (isRawContent) {
                BufferedReader(StringReader(source))
            } else if (effectiveSource.startsWith("http://", ignoreCase = true) || effectiveSource.startsWith("https://", ignoreCase = true)) {
                val inputStream = m3uDownloader.downloadStream(effectiveSource.trim())
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 16384)
            } else {
                // Don't leave an empty playlist behind (but never delete one we were refreshing)
                if (existing == null) playlistRepository.deletePlaylist(playlistId)
                emit(ImportProgress.Error("Invalid source URL or format"))
                return@flow
            }

            emit(ImportProgress.Loading("Parsing channels and categories..."))

            // 2. Stream-parse entries in chunks so memory stays flat on 50k+ channel lists.
            // Category ids are the ones Room generates: channels must reference those, never
            // a local counter (a counter breaks the FK as soon as a second playlist is added).
            val categoryIdByName = mutableMapOf<String, Long>()
            var totalChannels = 0
            var epgUrl: String? = null

            reader.use { bufReader ->
                val entries = M3uParser.parse(bufReader) { header ->
                    // Providers sometimes list several guides; the first one is the primary.
                    epgUrl = header.split(',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                }

                for (chunk in entries.chunked(500)) {
                    fun groupOf(groupTitle: String?) =
                        groupTitle?.trim()?.ifEmpty { null } ?: "General"

                    // New categories in this chunk, in first-seen order
                    val newNames = chunk.map { groupOf(it.groupTitle) }
                        .distinct()
                        .filter { it !in categoryIdByName }

                    if (newNames.isNotEmpty()) {
                        val firstOrder = categoryIdByName.size
                        val ids = categoryRepository.saveCategories(
                            newNames.mapIndexed { i, name ->
                                Category(
                                    playlistId = playlistId,
                                    name = name,
                                    type = StreamType.LIVE,
                                    sortOrder = firstOrder + i
                                )
                            }
                        )
                        newNames.zip(ids).forEach { (name, id) -> categoryIdByName[name] = id }
                    }

                    val channels = chunk.mapIndexed { i, entry ->
                        val group = groupOf(entry.groupTitle)
                        Channel(
                            playlistId = playlistId,
                            categoryId = categoryIdByName.getValue(group),
                            name = entry.rawName,
                            cleanName = entry.cleanName,
                            streamUrl = entry.streamUrl,
                            logoUrl = entry.logoUrl,
                            streamType = StreamType.LIVE,
                            tvgId = entry.tvgId,
                            tvgName = entry.tvgName,
                            groupTitle = group,
                            // Keep the order of the original playlist
                            sortOrder = totalChannels + i
                        )
                    }

                    channelRepository.saveChannels(channels)
                    totalChannels += channels.size
                    emit(ImportProgress.Parsing(totalChannels))
                }
            }

            emit(ImportProgress.Success(playlistId, totalChannels, epgUrl))

        } catch (e: OutOfMemoryError) {
            emit(ImportProgress.Error("Memoria insufficiente: la playlist e' troppo grande per questo dispositivo"))
        } catch (e: Exception) {
            emit(ImportProgress.Error("Import failed: ${e.localizedMessage ?: e.message}"))
        }
    }.flowOn(ioDispatcher)
}
