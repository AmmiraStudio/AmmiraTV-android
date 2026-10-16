package com.ammiratv.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * Recognises Italian content in provider category names, so the pinned folders ("Calcio Italia",
 * "Film Italiani", "Serie TV Italiane") fill themselves for any user's playlist.
 *
 * Providers have no common convention, so this matches several: a leading language tag
 * (`[IT] COMMEDIA`, `IT| DRAMMA`, `ITA - FILM`, `EU | IT SPORTS`), a parenthesised tag, or the
 * word itself anywhere (`ITALY SPORTS`, `ITALIA CALCIO`, `FILM ITALIANI`).
 *
 * A bare "IT" in the middle of a name is deliberately NOT enough: "[EN] THE IT CROWD" must not
 * count as Italian.
 */
object ItalianCategories {

    private val ITALIAN_WORDS = setOf(
        "ITALIA", "ITALY", "ITALIAN", "ITALIANO", "ITALIANI", "ITALIANA", "ITALIANE"
    )
    private val LANGUAGE_TAGS = setOf("IT", "ITA")
    private val REGION_TAGS = setOf("EU", "EUR", "EUROPE", "UE")
    private val TAG_IN_BRACKETS = Regex("""[\[(]\s*(IT|ITA)\s*[\])]""")

    private val SPORT_WORDS = setOf("SPORT", "SPORTS", "SPORTIVI", "SPORTIVO", "CALCIO", "SOCCER")
    private val EVENT_WORDS = setOf("PPV", "EVENT", "EVENTS", "EVENTO", "EVENTI")

    private fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.ROOT)

    private fun tokens(name: String): List<String> =
        normalize(name).split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }

    fun isItalian(name: String): Boolean {
        val upper = normalize(name)
        if (TAG_IN_BRACKETS.containsMatchIn(upper)) return true

        val t = tokens(name)
        if (t.isEmpty()) return false
        if (t.any { it in ITALIAN_WORDS }) return true
        if (t[0] in LANGUAGE_TAGS) return true
        // "EU | IT SPORTS": a region tag followed by the language tag
        return t.size >= 2 && t[0] in REGION_TAGS && t[1] in LANGUAGE_TAGS
    }

    /**
     * How strongly [name] looks like Italian football/sports. 0 when it is not Italian at all.
     * "ITALY SPORTS" outranks "ITALY DAZN SERIE A", and pay-per-view/event groups are penalised
     * so they never win the pinned slot.
     */
    fun sportsScore(name: String): Int {
        if (!isItalian(name)) return 0
        val t = tokens(name)
        var score = 0
        if (t.any { it in SPORT_WORDS }) score += 4
        if (t.zipWithNext().any { (a, b) -> a == "SERIE" && b == "A" }) score += 2
        if ("DAZN" in t) score += 1
        if ("SKY" in t) score += 1
        if ("FOOTBALL" in t) score += 2
        if (t.any { it in EVENT_WORDS }) score -= 2
        return score.coerceAtLeast(0)
    }

    /**
     * Whether an Italian category is a football/sports section: it names sport or calcio, DAZN,
     * football, or Serie A/B. Pay-per-view bundles ("PPV-LIVE EVENTS") are a different product and
     * stay out; "DAZN EVENTO" is in because it carries the DAZN tag.
     */
    fun isSportsSection(name: String): Boolean {
        if (!isItalian(name)) return false
        val t = tokens(name)
        if ("PPV" in t) return false
        val serieAorB = t.zipWithNext().any { (a, b) -> a == "SERIE" && (b == "A" || b == "B") }
        return t.any { it in SPORT_WORDS } || "DAZN" in t || "FOOTBALL" in t || serieAorB
    }

    /**
     * Every Italian football/sports section among [candidates], most "sporty" first, so that a
     * plain "ITALY SPORTS" group leads the pinned "Calcio Italia" folder ahead of DAZN groups.
     */
    fun sportsSections(candidates: List<Category>): List<Category> =
        candidates
            .filter { isSportsSection(it.name) }
            .sortedWith(
                compareByDescending<Category> { sportsScore(it.name) }.thenBy { it.name.length }
            )
}
