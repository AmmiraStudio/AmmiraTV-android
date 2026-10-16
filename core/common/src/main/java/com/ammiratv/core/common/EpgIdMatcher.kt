package com.ammiratv.core.common

import java.text.Normalizer
import java.util.Locale

/**
 * Deterministic matching of a channel name to an XMLTV channel id, for playlists whose
 * `tvg-id` is missing or wrong. Rules only, no ML: exact normalised match first, then a
 * one-edit-away match that must be unambiguous.
 *
 * Example: "IT: Sky Sport 1 HD" and "SkySport1.it@SD" both normalise to "skysport1".
 */
object EpgIdMatcher {

    private val QUALITY = Regex("\\b(uhd|fhd|hd|sd|4k|hevc|h264|h265|1080p|720p)\\b")
    private val COUNTRY_SUFFIX = Regex("\\.[a-z]{2,3}$")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /** Channels shorter than this are never fuzzy-matched: one edit changes their identity. */
    private const val MIN_LENGTH_FOR_FUZZY = 6

    fun normalize(raw: String): String {
        var s = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        s = COMBINING_MARKS.replace(s, "")
        s = s.substringBefore('@')             // "Rai1.it@SD" -> "Rai1.it"
        s = ChannelNameCleaner.clean(s).lowercase(Locale.ROOT) // drops "IT:" prefixes, tags
        s = COUNTRY_SUFFIX.replace(s, "")      // "rai1.it" -> "rai1"
        s = QUALITY.replace(s, " ")
        return NON_ALNUM.replace(s, "")
    }

    /**
     * @param candidates XMLTV channel ids that actually have programmes
     * @return the id to use for [channelName], or null when nothing is certain
     */
    fun bestMatch(channelName: String, candidates: Collection<String>): String? =
        Index(candidates).bestMatch(channelName)

    /**
     * Candidate ids normalised once. Matching tens of thousands of channels against thousands of
     * guide ids must not re-normalise every id per channel (that took minutes on a Fire TV Stick).
     */
    class Index(candidates: Collection<String>) {
        private val byNormalized = HashMap<String, String>(candidates.size * 2)
        // Fuzzy candidates grouped by length: a one-edit match is at most one character longer/shorter
        private val byLength = HashMap<Int, MutableList<Pair<String, String>>>()

        init {
            for (id in candidates) {
                val norm = normalize(id)
                if (norm.isEmpty()) continue
                if (byNormalized.putIfAbsent(norm, id) == null && norm.length >= MIN_LENGTH_FOR_FUZZY) {
                    byLength.getOrPut(norm.length) { mutableListOf() }.add(norm to id)
                }
            }
        }

        val isEmpty: Boolean get() = byNormalized.isEmpty()

        fun bestMatch(channelName: String): String? {
            val target = normalize(channelName)
            if (target.isEmpty()) return null

            byNormalized[target]?.let { return it }
            if (target.length < MIN_LENGTH_FOR_FUZZY) return null

            var found: String? = null
            for (len in (target.length - 1)..(target.length + 1)) {
                val bucket = byLength[len] ?: continue
                for ((norm, id) in bucket) {
                    if (withinOneEdit(target, norm)) {
                        if (found != null && found != id) return null // two answers: we don't know
                        found = id
                    }
                }
            }
            return found
        }
    }

    /** True when the strings differ by at most one insertion, deletion or substitution. */
    internal fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return when {
            a.length == b.length -> a.substring(i + 1) == b.substring(i + 1)
            a.length > b.length -> a.substring(i + 1) == b.substring(i)
            else -> a.substring(i) == b.substring(i + 1)
        }
    }
}
