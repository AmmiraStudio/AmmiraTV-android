package com.ammiratv.core.numbering

import com.ammiratv.core.model.ItalianCategories
import java.util.Locale

/** A live channel as seen by the numbering engine; decoupled from database/domain types. */
data class NumberingInput(
    val id: Long,
    /** Identity shared by every variant of one channel (same as DuplicateMerger.keyOf). */
    val key: String,
    val rawName: String,
    val tvgId: String?,
    val tvgName: String?,
    /** Provider group / category name, when known. */
    val groupName: String?,
    val streamUrl: String,
    val isLive: Boolean
)

enum class NumberOrigin { CATALOG, MANUAL, UNOFFICIAL }

data class NumberAssignment(
    val channelId: Long,
    val number: Int,
    val origin: NumberOrigin,
    val canonicalId: String? = null
)

data class NumberingResult(
    val assignments: List<NumberAssignment>,
    /** Italian live channels that got no catalogue number: the "da verificare" list. */
    val toVerify: List<Long>
)

object ChannelNumbering {

    /** First number of the clearly-unofficial range. */
    const val UNOFFICIAL_START = 1000

    private val VOD_WORDS = setOf("FILM", "FILMS", "MOVIE", "MOVIES", "VOD", "SERIES", "SERIETV")
    private val ALLOWED_TAGS = setOf("IT", "ITA", "EU", "EUR", "UE")
    private val FOREIGN_TAG = Regex("""^\W*\[?([A-Z]{2,3})\]?\s*[|:\-\]]""")

    /** Group titles and URLs that mean "this is on-demand content, not a live channel". */
    fun looksLikeVod(groupName: String?, streamUrl: String): Boolean {
        val url = streamUrl.lowercase(Locale.ROOT)
        if (url.contains("/movie/") || url.contains("/series/")) return true
        val tokens = groupName.orEmpty().uppercase(Locale.ROOT).split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
        if (tokens.any { it in VOD_WORDS }) return true
        // "SERIE TV" / "SERIE ITALIANA" are series groups, but "SERIE A" / "SERIE B" are football
        return tokens.zipWithNext().any { (a, b) -> a == "SERIE" && b != "A" && b != "B" }
    }

    fun hasItalianSignal(input: NumberingInput): Boolean =
        (input.groupName?.let { ItalianCategories.isItalian(it) } == true) ||
            ItalianCategories.isItalian(input.rawName) ||
            input.tvgId?.trim()?.endsWith(".it", ignoreCase = true) == true

    private fun hasForeignTag(input: NumberingInput): Boolean {
        for (text in listOfNotNull(input.groupName, input.rawName)) {
            val tag = FOREIGN_TAG.find(text.uppercase(Locale.ROOT))?.groupValues?.get(1) ?: continue
            if (tag !in ALLOWED_TAGS) return true
        }
        return false
    }

    /**
     * Computes numbers for [inputs].
     *
     * @param manual user overrides by channel key; they always win.
     * @param includeUnofficial give Italian live channels that are not in the catalogue a number
     *        from [UNOFFICIAL_START] upwards, alphabetically, so they cannot be mistaken for real ones.
     */
    fun assign(
        inputs: List<NumberingInput>,
        matcher: CanonicalMatcher,
        manual: Map<String, Int> = emptyMap(),
        includeUnofficial: Boolean = false
    ): NumberingResult {
        val out = ArrayList<NumberAssignment>(inputs.size / 8)
        val toVerify = ArrayList<Long>()
        val unofficial = ArrayList<NumberingInput>()

        for (input in inputs) {
            if (!input.isLive) continue
            if (looksLikeVod(input.groupName, input.streamUrl)) continue

            val manualNumber = manual[input.key]
            if (manualNumber != null) {
                out += NumberAssignment(input.id, manualNumber, NumberOrigin.MANUAL)
                continue
            }

            val italian = hasItalianSignal(input)
            val match = matcher.match(input.tvgId, input.rawName, input.tvgName)
            val accepted = match != null &&
                (match.reason == MatchReason.TVG_ID || italian) &&
                !(hasForeignTag(input) && !italian)

            when {
                accepted -> out += NumberAssignment(input.id, match!!.channel.number, NumberOrigin.CATALOG, match.channel.id)
                italian -> {
                    toVerify += input.id
                    unofficial += input
                }
            }
        }

        if (includeUnofficial && unofficial.isNotEmpty()) {
            // One number per channel identity: SD/HD/FHD variants share it
            val keys = unofficial
                .groupBy { it.key }
                .entries
                .sortedBy { (_, list) -> NameKeys.normalized(list.first().rawName) }
            var next = UNOFFICIAL_START
            for ((_, variants) in keys) {
                for (v in variants) out += NumberAssignment(v.id, next, NumberOrigin.UNOFFICIAL)
                next++
            }
        }

        return NumberingResult(out, toVerify)
    }

    /**
     * Picks which variant to play for a number: best picture, then favourite, then reliability,
     * then the earliest in the list. [qualityOf] receives the raw name.
     */
    fun <T> bestVariant(
        candidates: List<T>,
        rawName: (T) -> String,
        isFavorite: (T) -> Boolean,
        reliability: (T) -> Int,
        order: (T) -> Long
    ): T? = candidates.maxWithOrNull(
        compareBy<T> { NameKeys.qualityRank(rawName(it)) }
            .thenBy { if (isFavorite(it)) 1 else 0 }
            .thenBy { reliability(it) }
            .thenByDescending { order(it) }
    )
}
