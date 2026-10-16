package com.ammiratv.domain.usecase

import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.core.model.Recommendation
import com.ammiratv.core.model.TonightRecommender
import com.ammiratv.domain.repository.ChannelRepository
import com.ammiratv.domain.repository.EpgRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** A recommendation together with the channel to watch/record it on, when known. */
data class TonightItem(
    val recommendation: Recommendation,
    val channel: Channel?
)

class WhatToWatchTonightUseCase @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository,
    private val preferences: AmmiraTvPreferences
) {
    suspend operator fun invoke(profileId: Long): List<TonightItem> {
        val now = System.currentTimeMillis()
        val favorites = channelRepository.getFavorites(profileId).first()
        val favoriteIds = favorites.mapNotNull { it.tvgId }.filter { it.isNotBlank() }.toSet()
        val keywords = preferences.keywords.first()

        val fromFavorites: List<EpgProgram> =
            if (favoriteIds.isEmpty()) emptyList()
            else epgRepository.getProgramsForChannels(favoriteIds.toList(), now, now + 8 * 3600 * 1000L)
                .first().values.flatten()

        // Keyword hits can be on any channel in the guide, not only favourites
        val fromKeywords = keywords.flatMap { epgRepository.searchPrograms(it) }

        val all = (fromFavorites + fromKeywords).distinctBy { it.channelTvgId to it.startTime }

        val ranked = TonightRecommender.rank(
            programs = all,
            favoriteChannelIds = favoriteIds,
            keywords = keywords,
            nowMs = now
        )

        val channelByTvg = favorites.filter { !it.tvgId.isNullOrBlank() }.associateBy { it.tvgId!! }
        return ranked.map { rec ->
            val channel = channelByTvg[rec.program.channelTvgId]
                ?: channelRepository.getChannelsByTvgId(rec.program.channelTvgId).firstOrNull()
            TonightItem(rec, channel)
        }
    }
}

class KeywordRulesUseCase @Inject constructor(
    private val preferences: AmmiraTvPreferences
) {
    fun observe(): Flow<Set<String>> = preferences.keywords
    suspend fun add(keyword: String) = preferences.addKeyword(keyword)
    suspend fun remove(keyword: String) = preferences.removeKeyword(keyword)
}
