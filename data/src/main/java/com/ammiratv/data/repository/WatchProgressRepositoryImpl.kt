package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.WatchProgressDao
import com.ammiratv.core.database.entity.WatchProgressEntity
import com.ammiratv.core.model.VodKind
import com.ammiratv.core.model.WatchProgress
import com.ammiratv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepositoryImpl @Inject constructor(
    private val dao: WatchProgressDao
) : WatchProgressRepository {

    override fun observeContinueWatching(profileId: Long): Flow<List<WatchProgress>> =
        dao.observeContinueWatching(profileId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(profileId: Long): Flow<Map<String, WatchProgress>> =
        dao.observeAll(profileId).map { list -> list.associate { it.contentKey to it.toDomain() } }

    override suspend fun get(profileId: Long, contentKey: String): WatchProgress? =
        dao.get(profileId, contentKey)?.toDomain()

    override suspend fun save(progress: WatchProgress) = dao.upsert(progress.toEntity())

    override suspend fun setWatched(profileId: Long, contentKey: String, watched: Boolean) =
        dao.setWatched(profileId, contentKey, watched)
}

private fun WatchProgressEntity.toDomain() = WatchProgress(
    profileId = profileId,
    contentKey = contentKey,
    kind = runCatching { VodKind.valueOf(kind) }.getOrDefault(VodKind.MOVIE),
    playlistId = playlistId,
    title = title,
    subtitle = subtitle,
    posterUrl = posterUrl,
    streamUrl = streamUrl,
    seriesId = seriesId,
    positionMs = positionMs,
    durationMs = durationMs,
    isWatched = isWatched,
    updatedAt = updatedAt
)

private fun WatchProgress.toEntity() = WatchProgressEntity(
    profileId = profileId,
    contentKey = contentKey,
    kind = kind.name,
    playlistId = playlistId,
    title = title,
    subtitle = subtitle,
    posterUrl = posterUrl,
    streamUrl = streamUrl,
    seriesId = seriesId,
    positionMs = positionMs,
    durationMs = durationMs,
    isWatched = isWatched,
    updatedAt = updatedAt
)
