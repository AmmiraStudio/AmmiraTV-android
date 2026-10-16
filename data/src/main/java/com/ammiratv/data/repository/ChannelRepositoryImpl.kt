package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.ChannelDao
import com.ammiratv.core.database.dao.ProfileFavoriteDao
import com.ammiratv.core.database.entity.ChannelEntity
import com.ammiratv.core.database.entity.ProfileFavoriteEntity
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.StreamType
import com.ammiratv.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val favoriteDao: ProfileFavoriteDao
) : ChannelRepository {

    override fun getChannelsByCategory(categoryId: Long): Flow<List<Channel>> {
        return channelDao.getByCategory(categoryId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getChannelsByPlaylist(playlistId: Long): Flow<List<Channel>> {
        return channelDao.getByPlaylist(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFavorites(profileId: Long): Flow<List<Channel>> {
        return favoriteDao.getFavoriteChannels(profileId).map { entities ->
            entities.map { it.toDomain().copy(isFavorite = true) }
        }
    }

    override fun getFavoriteIds(profileId: Long): Flow<Set<Long>> {
        return favoriteDao.getFavoriteIds(profileId).map { it.toSet() }
    }

    override suspend fun snapshotFavorites(playlistId: Long): List<Pair<Long, String>> {
        return favoriteDao.getFavoriteUrls(playlistId).map { it.profileId to it.streamUrl }
    }

    override suspend fun restoreFavorites(playlistId: Long, favorites: List<Pair<Long, String>>) {
        favorites.forEach { (profileId, url) -> favoriteDao.restore(profileId, playlistId, url) }
    }

    override suspend fun getChannelById(id: Long): Channel? {
        return channelDao.getById(id)?.toDomain()
    }

    override suspend fun getLiveChannels(playlistId: Long): List<Channel> =
        channelDao.getLiveByPlaylist(playlistId).map { it.toDomain() }

    override suspend fun setTvgId(channelId: Long, tvgId: String) = channelDao.setTvgId(channelId, tvgId)

    override suspend fun forEachLiveChannelPage(
        playlistId: Long,
        pageSize: Int,
        block: suspend (List<Channel>) -> Unit
    ) {
        var afterId = 0L
        while (true) {
            val page = channelDao.getLivePage(playlistId, afterId, pageSize)
            if (page.isEmpty()) return
            block(page.map { it.toDomain() })
            afterId = page.last().id
            if (page.size < pageSize) return
        }
    }

    override suspend fun setTvgIds(pairs: List<Pair<Long, String>>) {
        if (pairs.isNotEmpty()) channelDao.setTvgIds(pairs)
    }

    override suspend fun getChannelsByIds(ids: Collection<Long>): List<Channel> =
        ids.distinct().chunked(500).flatMap { chunk -> channelDao.getByIds(chunk).map { it.toDomain() } }

    override suspend fun getChannelsByTvgId(tvgId: String): List<Channel> {
        return channelDao.getByTvgId(tvgId).map { it.toDomain() }
    }

    override suspend fun searchChannels(query: String): List<Channel> {
        return channelDao.search(query).map { it.toDomain() }
    }

    override suspend fun setFavorite(profileId: Long, channelId: Long, isFavorite: Boolean) {
        if (isFavorite) {
            favoriteDao.add(ProfileFavoriteEntity(profileId, channelId))
        } else {
            favoriteDao.remove(profileId, channelId)
        }
    }

    override suspend fun updateWatchProgress(channelId: Long, positionMs: Long) {
        channelDao.updateWatchProgress(channelId, System.currentTimeMillis(), positionMs)
    }

    override suspend fun saveChannels(channels: List<Channel>) {
        channelDao.insertAll(channels.map { it.toEntity() })
    }

    override suspend fun getChannelCount(playlistId: Long): Int {
        return channelDao.countByPlaylist(playlistId)
    }

    override suspend fun deleteChannelsByPlaylist(playlistId: Long) {
        channelDao.deleteByPlaylist(playlistId)
    }

    override suspend fun findAlternatives(channel: Channel): List<Channel> {
        return channelDao.findAlternatives(
            excludeId = channel.id,
            tvgId = channel.tvgId,
            cleanName = channel.cleanName
        ).map { it.toDomain() }
    }

    override suspend fun updateReliability(channelId: Long, delta: Int) {
        channelDao.updateReliability(channelId, delta)
    }

    override suspend fun replaceChannelNumbers(playlistId: Long, numbers: Map<Long, Int>) {
        channelDao.replaceChannelNumbers(playlistId, numbers)
    }

    override suspend fun getChannelsByNumber(playlistId: Long, number: Int): List<Channel> =
        channelDao.getByNumber(playlistId, number).map { it.toDomain() }
}

// ── Mappers ──────────────────────────────────────────────────────────────

private fun ChannelEntity.toDomain() = Channel(
    id = id,
    playlistId = playlistId,
    categoryId = categoryId,
    name = name,
    cleanName = cleanName,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    streamType = StreamType.entries.getOrElse(streamType) { StreamType.LIVE },
    tvgId = tvgId,
    tvgName = tvgName,
    groupTitle = groupTitle,
    isFavorite = isFavorite,
    isHidden = isHidden,
    sortOrder = sortOrder,
    reliability = reliability,
    lastWatched = lastWatched,
    watchPosition = watchPosition,
    channelNumber = channelNumber
)

private fun Channel.toEntity() = ChannelEntity(
    id = id,
    playlistId = playlistId,
    categoryId = categoryId,
    name = name,
    cleanName = cleanName,
    streamUrl = streamUrl,
    logoUrl = logoUrl,
    streamType = streamType.ordinal,
    tvgId = tvgId,
    tvgName = tvgName,
    groupTitle = groupTitle,
    isFavorite = isFavorite,
    isHidden = isHidden,
    sortOrder = sortOrder,
    reliability = reliability,
    lastWatched = lastWatched,
    watchPosition = watchPosition,
    channelNumber = channelNumber
)
