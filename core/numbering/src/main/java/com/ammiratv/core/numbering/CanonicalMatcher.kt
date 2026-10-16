package com.ammiratv.core.numbering

import java.util.Locale

/** How a provider channel was tied to a canonical one, strongest first. */
enum class MatchReason { TVG_ID, NAME, ALIAS, FUZZY }

data class CanonicalMatch(val channel: CanonicalChannel, val reason: MatchReason)

/**
 * Matches provider channels to the canonical database, in cascade:
 *  1. `tvg-id` listed in the canonical entry;
 *  2. normalised name equal to the canonical name;
 *  3. normalised name equal to an alias;
 *  4. fuzzy: only when the digit sequence is identical, the token count is identical and the
 *     difference is a single letter, and exactly one candidate qualifies.
 *
 * Anything ambiguous returns null: a wrong number is worse than no number.
 */
class CanonicalMatcher(database: CanonicalDatabase) {

    private val byTvgId = HashMap<String, CanonicalChannel>()
    private val byName = HashMap<String, CanonicalChannel>()
    private val byAlias = HashMap<String, CanonicalChannel>()
    private val ambiguousKeys = HashSet<String>()
    private val allKeys: List<Pair<String, CanonicalChannel>>

    init {
        val keyed = ArrayList<Pair<String, CanonicalChannel>>()
        for (c in database.channels) {
            for (t in c.tvgIds) {
                val k = t.trim().lowercase(Locale.ROOT)
                if (k.isNotEmpty()) byTvgId.putIfAbsent(k, c)
            }
            put(byName, NameKeys.compact(c.name), c, keyed)
            for (a in c.aliases) put(byAlias, NameKeys.compact(a), c, keyed)
        }
        allKeys = keyed
    }

    private fun put(
        map: HashMap<String, CanonicalChannel>,
        key: String,
        channel: CanonicalChannel,
        keyed: MutableList<Pair<String, CanonicalChannel>>
    ) {
        if (key.isEmpty()) return
        val existing = map[key] ?: byName[key] ?: byAlias[key]
        if (existing != null && existing.id != channel.id) {
            ambiguousKeys += key // two canonical channels claim it: never match on it
            return
        }
        map[key] = channel
        keyed += key to channel
    }

    fun match(tvgId: String?, rawName: String, tvgName: String? = null): CanonicalMatch? {
        val tvg = tvgId?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (tvg.isNotEmpty()) byTvgId[tvg]?.let { return CanonicalMatch(it, MatchReason.TVG_ID) }

        for (candidateName in listOfNotNull(rawName, tvgName)) {
            val key = NameKeys.compact(candidateName)
            if (key.isEmpty() || key in ambiguousKeys) continue
            byName[key]?.let { return CanonicalMatch(it, MatchReason.NAME) }
            byAlias[key]?.let { return CanonicalMatch(it, MatchReason.ALIAS) }
        }

        val key = NameKeys.compact(rawName)
        return fuzzy(key)
    }

    private fun fuzzy(key: String): CanonicalMatch? {
        if (key.length < 6 || key in ambiguousKeys) return null
        val digits = NameKeys.digitSequence(key)
        val hits = allKeys.filter { (k, _) ->
            k.length >= 6 &&
                NameKeys.digitSequence(k) == digits &&
                k !in ambiguousKeys &&
                withinOneEdit(k, key)
        }.map { it.second }.distinctBy { it.id }
        return hits.singleOrNull()?.let { CanonicalMatch(it, MatchReason.FUZZY) }
    }

    /** True when [a] and [b] differ by at most one inserted, deleted or replaced character. */
    internal fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
