package com.ammiratv.core.numbering

import com.ammiratv.core.common.ChannelNameCleaner
import java.text.Normalizer
import java.util.Locale

/** Turns messy provider names into comparable keys. Reuses the app's [ChannelNameCleaner]. */
object NameKeys {

    private val DECORATION = Regex("[\\p{So}\\p{Sk}\\u2726\\u25CF\\u2605\\u2606]+")
    private val ACCENTS = Regex("\\p{M}+")
    // Keep letters, digits, '+' (for "+24"), and '.' only when it sits between digits ("102.5")
    private val NOT_KEPT = Regex("[^a-z0-9+. ]")
    private val LONE_DOT = Regex("(?<!\\d)\\.|\\.(?!\\d)")

    /**
     * "✦●✦ IT| RAI 1 HD [1080p] ✦●✦" -> "rai 1". Decorations, country tags, quality and backup tags
     * go (via [ChannelNameCleaner]), accents are folded, punctuation becomes spaces.
     */
    fun normalized(raw: String): String {
        if (raw.isBlank()) return ""
        val undecorated = DECORATION.replace(raw, " ")
        val cleaned = ChannelNameCleaner.clean(undecorated)
        val folded = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
            .replace(ACCENTS, "")
            .lowercase(Locale.ROOT)
        return folded
            .replace(NOT_KEPT, " ")
            .replace(LONE_DOT, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Spacing-insensitive key: "rai 1", "rai1" and "RAI-1" all become "rai1". */
    fun compact(raw: String): String = normalized(raw).replace(" ", "")

    /** Digits-only fingerprint; two names with different digit sequences are never the same channel. */
    fun digitSequence(key: String): String = key.filter { it.isDigit() }

    /** Rough picture quality encoded in the raw name, for choosing the variant to play. */
    fun qualityRank(raw: String): Int {
        val u = raw.uppercase(Locale.ROOT)
        return when {
            Regex("\\b(4K|UHD|2160P?)\\b").containsMatchIn(u) -> 4
            Regex("\\b(FHD|1080[PI]?)\\b").containsMatchIn(u) -> 3
            Regex("\\bHD\\b").containsMatchIn(u) || Regex("\\b720P?\\b").containsMatchIn(u) -> 2
            Regex("\\bSD\\b").containsMatchIn(u) -> 0
            else -> 1
        }
    }
}
