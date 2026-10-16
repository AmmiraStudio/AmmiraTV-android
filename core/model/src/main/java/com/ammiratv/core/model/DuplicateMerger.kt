package com.ammiratv.core.model

import java.util.Locale

/**
 * Smart Channel Cleaner, duplicates part: the same channel listed several times
 * ("Rai 1 HD", "Rai 1 FHD", "Rai 1 (backup)") becomes one entry. The others stay in the
 * database so failover can still switch to them.
 */
object DuplicateMerger {

    /** Identity of a channel for merging: the guide id when present, else the cleaned name. */
    fun keyOf(channel: Channel): String {
        val tvg = channel.tvgId?.trim()?.lowercase(Locale.ROOT)
        return if (!tvg.isNullOrEmpty()) "tvg:$tvg" else "name:${channel.cleanName.trim().lowercase(Locale.ROOT)}"
    }

    /**
     * Keeps list order (first occurrence of each group) and, within a group, shows the best copy:
     * a favourite first, then the highest reliability, then the earliest in the playlist.
     */
    fun merge(channels: List<Channel>): List<Channel> {
        if (channels.size < 2) return channels

        val best = HashMap<String, Channel>()
        val order = ArrayList<String>()
        for (channel in channels) {
            val key = keyOf(channel)
            val current = best[key]
            if (current == null) {
                best[key] = channel
                order += key
            } else if (isBetter(channel, current)) {
                best[key] = channel
            }
        }
        return order.map { best.getValue(it) }
    }

    private fun isBetter(candidate: Channel, current: Channel): Boolean = when {
        candidate.isFavorite != current.isFavorite -> candidate.isFavorite
        candidate.reliability != current.reliability -> candidate.reliability > current.reliability
        else -> false // keep the earlier one
    }
}
