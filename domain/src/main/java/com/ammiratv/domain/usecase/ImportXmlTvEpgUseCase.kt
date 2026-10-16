package com.ammiratv.domain.usecase

import com.ammiratv.core.common.Dispatcher
import com.ammiratv.core.common.AmmiraTvDispatchers
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.core.network.epg.XmlTvParser
import com.ammiratv.core.network.m3u.M3uDownloader
import com.ammiratv.domain.repository.EpgRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class ImportXmlTvEpgUseCase @Inject constructor(
    private val epgRepository: EpgRepository,
    private val m3uDownloader: M3uDownloader,
    @Dispatcher(AmmiraTvDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {

    operator fun invoke(
        epgUrl: String,
        rawContent: String? = null
    ): Flow<ImportProgress> = flow {
        emit(ImportProgress.Loading("Scaricamento guida TV (XMLTV)..."))
        var importedSource: Pair<Long, Long>? = null

        try {
            val stream: InputStream = if (!rawContent.isNullOrEmpty()) {
                ByteArrayInputStream(rawContent.toByteArray(StandardCharsets.UTF_8))
            } else if (epgUrl.startsWith("http://", ignoreCase = true) || epgUrl.startsWith("https://", ignoreCase = true)) {
                m3uDownloader.downloadStream(epgUrl.trim())
            } else {
                emit(ImportProgress.Error("URL sorgente EPG non valido"))
                return@flow
            }

            emit(ImportProgress.Loading("Elaborazione programmi EPG in streaming..."))

            // The stream is already open, so a download failure above never wipes the old guide.
            val sourceKey = if (rawContent.isNullOrEmpty()) epgUrl.trim() else "local"
            val sourceId = epgRepository.getOrCreateSource(sourceKey, sourceName(sourceKey))
            // The old guide stays in place until the new one is fully in: an interrupted download
            // (weak Wi-Fi, low memory) must never leave the user with an empty guide.
            val oldRowsUpTo = epgRepository.watermark(sourceId)
            importedSource = sourceId to oldRowsUpTo

            // Programs that ended long ago are never shown: skip them instead of storing them.
            val now = System.currentTimeMillis()
            val oldestUseful = now - 2 * 3600 * 1000L
            // A week of guide for tens of thousands of channels is millions of rows: keep what the
            // grid can reasonably show (2 days), which keeps the import fast and the database small.
            val newestUseful = now + 48 * 3600 * 1000L

            var totalPrograms = 0
            stream.use { inStream ->
                val relevant = XmlTvParser.parse(inStream).filter { it.endTime > oldestUseful && it.startTime < newestUseful }
                for (chunk in relevant.chunked(500)) {
                    epgRepository.savePrograms(
                        chunk.map { p ->
                            EpgProgram(
                                epgSourceId = sourceId,
                                channelTvgId = p.channelTvgId,
                                title = p.title,
                                description = p.description?.take(600),
                                startTime = p.startTime,
                                endTime = p.endTime,
                                category = p.category,
                                posterUrl = p.iconUrl
                            )
                        }
                    )
                    totalPrograms += chunk.size
                    emit(ImportProgress.Parsing(totalPrograms))
                }
            }

            epgRepository.dropRowsUpTo(sourceId, oldRowsUpTo)
            epgRepository.markSourceUpdated(sourceId)
            emit(ImportProgress.Success(playlistId = 0L, totalChannels = totalPrograms))

        } catch (e: OutOfMemoryError) {
            importedSource?.let { (id, mark) -> runCatching { epgRepository.dropRowsAfter(id, mark) } }
            emit(ImportProgress.Error("Memoria insufficiente per la guida TV su questo dispositivo"))
        } catch (e: Exception) {
            importedSource?.let { (id, mark) -> runCatching { epgRepository.dropRowsAfter(id, mark) } }
            emit(ImportProgress.Error("Importazione EPG fallita: ${e.localizedMessage ?: e.message}"))
        }
    }.flowOn(ioDispatcher)

    private fun sourceName(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "EPG"
}
