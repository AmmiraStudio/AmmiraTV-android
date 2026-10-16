package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Channel
import com.ammiratv.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FindAlternativeStreamUseCaseTest {

    private val fakeChannelRepository = object : ChannelRepository {
        val alternatives = mutableListOf<Channel>()
        var reliabilityUpdates = mutableListOf<Pair<Long, Int>>()

        override fun getChannelsByCategory(categoryId: Long) = flowOf(emptyList<Channel>())
        override fun getChannelsByPlaylist(playlistId: Long) = flowOf(emptyList<Channel>())
        override fun getFavorites(profileId: Long) = flowOf(emptyList<Channel>())
        override fun getFavoriteIds(profileId: Long) = flowOf(emptySet<Long>())
        override suspend fun snapshotFavorites(playlistId: Long) = emptyList<Pair<Long, String>>()
        override suspend fun restoreFavorites(playlistId: Long, favorites: List<Pair<Long, String>>) {}
        override suspend fun getLiveChannels(playlistId: Long) = emptyList<Channel>()
        override suspend fun setTvgId(channelId: Long, tvgId: String) {}
        override suspend fun forEachLiveChannelPage(
            playlistId: Long,
            pageSize: Int,
            block: suspend (List<Channel>) -> Unit
        ) {}
        override suspend fun setTvgIds(pairs: List<Pair<Long, String>>) {}
        override suspend fun getChannelsByIds(ids: Collection<Long>) = emptyList<Channel>()
        override suspend fun replaceChannelNumbers(playlistId: Long, numbers: Map<Long, Int>) {}
        override suspend fun getChannelsByNumber(playlistId: Long, number: Int) = emptyList<Channel>()
        override suspend fun getChannelById(id: Long) = null
        override suspend fun getChannelsByTvgId(tvgId: String) = emptyList<Channel>()
        override suspend fun searchChannels(query: String) = emptyList<Channel>()
        override suspend fun setFavorite(profileId: Long, channelId: Long, isFavorite: Boolean) {}
        override suspend fun updateWatchProgress(channelId: Long, positionMs: Long) {}
        override suspend fun saveChannels(channels: List<Channel>) {}
        override suspend fun getChannelCount(playlistId: Long) = 0
        override suspend fun deleteChannelsByPlaylist(playlistId: Long) {}
        override suspend fun findAlternatives(channel: Channel): List<Channel> = alternatives
        override suspend fun updateReliability(channelId: Long, delta: Int) {
            reliabilityUpdates.add(Pair(channelId, delta))
        }
    }

    private val useCase = FindAlternativeStreamUseCase(fakeChannelRepository)

    @Test
    fun testFindAlternativesReturnsBackupChannels() = runTest {
        val primaryChannel = Channel(id = 1, name = "Sky Sport 1 HD", cleanName = "Sky Sport 1", streamUrl = "http://stream1.ts", tvgId = "skysport1")
        val backupChannel = Channel(id = 2, name = "Sky Sport 1 FHD (Backup)", cleanName = "Sky Sport 1", streamUrl = "http://stream2.ts", tvgId = "skysport1", reliability = 5)

        fakeChannelRepository.alternatives.add(backupChannel)

        val result = useCase(primaryChannel)

        assertEquals(1, result.size)
        assertEquals(2L, result[0].id)
        assertEquals("http://stream2.ts", result[0].streamUrl)
    }

    @Test
    fun testRecordSuccessIncreasesReliability() = runTest {
        useCase.recordStreamSuccess(channelId = 10)
        assertEquals(listOf(Pair(10L, 1)), fakeChannelRepository.reliabilityUpdates)
    }

    @Test
    fun testRecordFailureDecreasesReliability() = runTest {
        useCase.recordStreamFailure(channelId = 10)
        assertEquals(listOf(Pair(10L, -2)), fakeChannelRepository.reliabilityUpdates)
    }
}
