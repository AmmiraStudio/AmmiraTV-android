package com.ammiratv.domain.usecase

import com.ammiratv.core.common.EpgIdMatcher
import com.ammiratv.domain.repository.ChannelRepository
import com.ammiratv.domain.repository.EpgRepository
import com.ammiratv.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Smart Channel Cleaner, EPG part: gives a guide to channels whose `tvg-id` is empty or
 * points to an id the guide doesn't contain. Rules are deterministic (see [EpgIdMatcher]).
 */
class AutoMatchEpgUseCase @Inject constructor(
    private val epgRepository: EpgRepository,
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository
) {
    /** @return how many channels were assigned a guide id */
    suspend operator fun invoke(): Int {
        val known = epgRepository.getKnownChannelIds()
        if (known.isEmpty()) return 0
        val knownSet = known.toHashSet()
        // Normalised once for the whole run, not once per channel
        val index = EpgIdMatcher.Index(known)

        // Every change to the channel table makes the open channel list reload completely, so
        // all matches are written at the end in ONE transaction instead of page after page.
        val updates = ArrayList<Pair<Long, String>>()
        for (playlist in playlistRepository.getPlaylists().first()) {
            channelRepository.forEachLiveChannelPage(playlist.id) { page ->
                for (channel in page) {
                    val current = channel.tvgId
                    if (!current.isNullOrBlank() && current in knownSet) continue // already fine

                    // The playlist's own tvg-name is usually cleaner than the display name
                    val match = index.bestMatch(channel.tvgName ?: channel.cleanName)
                        ?: index.bestMatch(channel.cleanName)
                        ?: continue
                    updates += channel.id to match
                }
            }
        }
        if (updates.isNotEmpty()) channelRepository.setTvgIds(updates)
        return updates.size
    }
}
