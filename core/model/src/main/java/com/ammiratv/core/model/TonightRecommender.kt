package com.ammiratv.core.model

import java.util.Calendar
import java.util.Locale

/** A suggested programme and the human-readable reasons behind its score. */
data class Recommendation(
    val program: EpgProgram,
    val score: Int,
    val reasons: List<String>
)

/**
 * "Cosa guardo stasera?" — version 1, rules only (no model, nothing leaves the device).
 * An LLM summariser can later sit behind the same `rank` output without touching the rules.
 */
object TonightRecommender {

    private const val WINDOW_MS = 8 * 3600 * 1000L

    // Weights: favourites are the strongest signal the user gave us explicitly
    private const val FAVORITE_CHANNEL = 50
    private const val KEYWORD_HIT = 40
    private const val ON_AIR_NOW = 20
    private const val STARTING_SOON = 15
    private const val PRIME_TIME = 10
    private const val CATEGORY_AFFINITY = 12

    fun rank(
        programs: Collection<EpgProgram>,
        favoriteChannelIds: Set<String>,
        keywords: Collection<String>,
        nowMs: Long,
        limit: Int = 20,
        /** Keyword matches may be further out than the evening window (e.g. a match on Sunday). */
        keywordHorizonMs: Long = 7 * 24 * 3600 * 1000L
    ): List<Recommendation> {
        val cleanKeywords = keywords.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }

        // Categories the user's favourite channels carry right now hint at their taste
        val categoryWeight = programs
            .filter { it.channelTvgId in favoriteChannelIds }
            .mapNotNull { it.category?.lowercase(Locale.ROOT) }
            .groupingBy { it }.eachCount()
        val topCategories = categoryWeight.entries.sortedByDescending { it.value }.take(3).map { it.key }.toSet()

        return programs
            .asSequence()
            .filter { it.endTime > nowMs }
            .mapNotNull { program ->
                val reasons = mutableListOf<String>()
                var score = 0
                val title = program.title.lowercase(Locale.ROOT)

                val keywordHit = cleanKeywords.firstOrNull { title.contains(it) }
                val inEveningWindow = program.startTime < nowMs + WINDOW_MS
                val inKeywordHorizon = program.startTime < nowMs + keywordHorizonMs

                if (keywordHit != null && inKeywordHorizon) {
                    score += KEYWORD_HIT
                    reasons += "Contiene \"$keywordHit\""
                }
                if (program.channelTvgId in favoriteChannelIds && inEveningWindow) {
                    score += FAVORITE_CHANNEL
                    reasons += "Sul tuo canale preferito"
                }
                // Only recommend what matched a rule: bare "airing now" noise is not a suggestion
                if (reasons.isEmpty()) return@mapNotNull null

                if (program.startTime <= nowMs) {
                    score += ON_AIR_NOW
                    reasons += "In onda ora"
                } else if (program.startTime - nowMs <= 3600 * 1000L) {
                    score += STARTING_SOON
                    reasons += "Inizia a breve"
                }
                if (isPrimeTime(program.startTime)) score += PRIME_TIME
                if (program.category?.lowercase(Locale.ROOT) in topCategories) {
                    score += CATEGORY_AFFINITY
                    reasons += "Categoria che guardi spesso"
                }

                Recommendation(program, score, reasons)
            }
            .sortedWith(compareByDescending<Recommendation> { it.score }.thenBy { it.program.startTime })
            .distinctBy { it.program.channelTvgId to it.program.startTime }
            .take(limit)
            .toList()
    }

    private fun isPrimeTime(startMs: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = startMs }.get(Calendar.HOUR_OF_DAY)
        return hour in 20..22
    }
}
