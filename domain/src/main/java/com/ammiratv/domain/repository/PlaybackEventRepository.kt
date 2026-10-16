package com.ammiratv.domain.repository

import com.ammiratv.core.model.PlaybackEvent

interface PlaybackEventRepository {
    suspend fun record(event: PlaybackEvent)
    suspend fun eventsSince(playlistId: Long, sinceMs: Long): List<PlaybackEvent>
    suspend fun pruneOlderThan(cutoffMs: Long)
}
