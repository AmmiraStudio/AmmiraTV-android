package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.PlaybackEventDao
import com.ammiratv.core.database.entity.PlaybackEventEntity
import com.ammiratv.core.model.PlaybackEvent
import com.ammiratv.core.model.PlaybackEventType
import com.ammiratv.domain.repository.PlaybackEventRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackEventRepositoryImpl @Inject constructor(
    private val dao: PlaybackEventDao
) : PlaybackEventRepository {

    override suspend fun record(event: PlaybackEvent) {
        dao.insert(
            PlaybackEventEntity(
                playlistId = event.playlistId,
                channelId = event.channelId,
                timestamp = event.timestamp,
                type = event.type.name,
                durationMs = event.durationMs
            )
        )
    }

    override suspend fun eventsSince(playlistId: Long, sinceMs: Long): List<PlaybackEvent> =
        dao.getSince(playlistId, sinceMs).mapNotNull { e ->
            val type = runCatching { PlaybackEventType.valueOf(e.type) }.getOrNull() ?: return@mapNotNull null
            PlaybackEvent(e.playlistId, e.channelId, e.timestamp, type, e.durationMs)
        }

    override suspend fun pruneOlderThan(cutoffMs: Long) = dao.deleteOlderThan(cutoffMs)
}
