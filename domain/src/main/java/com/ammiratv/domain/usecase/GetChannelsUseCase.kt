package com.ammiratv.domain.usecase

import com.ammiratv.core.common.AdultContentFilter
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.DuplicateMerger
import com.ammiratv.domain.repository.ChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetChannelsUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    /**
     * @param categoryId a category id, [FAVORITES_CATEGORY] for the profile's favourites,
     * or null/0 for the whole playlist.
     * @param hideAdult true for child profiles: adult groups/channels are removed from the list.
     */
    operator fun invoke(
        categoryId: Long?,
        playlistId: Long?,
        profileId: Long,
        hideAdult: Boolean = false,
        /** Collapse HD/FHD/backup copies of the same channel into the most reliable one. */
        mergeDuplicates: Boolean = false
    ): Flow<List<Channel>> {
        val source = when {
            categoryId == FAVORITES_CATEGORY -> channelRepository.getFavorites(profileId)
            categoryId != null && categoryId > 0 -> channelRepository.getChannelsByCategory(categoryId)
            playlistId != null && playlistId > 0 -> channelRepository.getChannelsByPlaylist(playlistId)
            else -> channelRepository.getFavorites(profileId)
        }

        // The favourites list is the user's own curation: never merge it
        val merge = mergeDuplicates && categoryId != FAVORITES_CATEGORY

        return combine(source, channelRepository.getFavoriteIds(profileId)) { channels, favoriteIds ->
            channels
                .filter { !hideAdult || !AdultContentFilter.isAdult(it.groupTitle, it.name) }
                // Copy only when the flag really differs: 50k copies cost memory and time on weak boxes
                .map { c ->
                    val fav = c.id in favoriteIds
                    if (fav == c.isFavorite) c else c.copy(isFavorite = fav)
                }
                .let { if (merge) DuplicateMerger.merge(it) else it }
        }
    }

    companion object {
        const val FAVORITES_CATEGORY = -1L
    }
}
