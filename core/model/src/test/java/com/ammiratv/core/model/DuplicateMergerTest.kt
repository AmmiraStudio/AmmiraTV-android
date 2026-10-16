package com.ammiratv.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateMergerTest {

    private fun ch(
        id: Long,
        clean: String,
        tvg: String? = null,
        reliability: Int = 0,
        favorite: Boolean = false
    ) = Channel(
        id = id, name = clean, cleanName = clean, streamUrl = "http://x/$id",
        tvgId = tvg, reliability = reliability, isFavorite = favorite
    )

    @Test
    fun sameCleanNameIsMergedKeepingTheMostReliable() {
        val merged = DuplicateMerger.merge(
            listOf(
                ch(1, "Rai 1", reliability = 2),
                ch(2, "Rai 2"),
                ch(3, "rai 1", reliability = 9)
            )
        )
        assertEquals(listOf(3L, 2L), merged.map { it.id })
    }

    @Test
    fun tvgIdWinsOverDifferentNames() {
        val merged = DuplicateMerger.merge(
            listOf(
                ch(1, "Sky Sport Uno", tvg = "skysport1"),
                ch(2, "Sky Sport 1", tvg = "SkySport1")
            )
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun favouriteCopyIsPreferred() {
        val merged = DuplicateMerger.merge(
            listOf(ch(1, "La7", reliability = 5), ch(2, "La7", favorite = true))
        )
        assertEquals(2L, merged.single().id)
    }

    @Test
    fun orderOfFirstOccurrenceIsKept() {
        val merged = DuplicateMerger.merge(
            listOf(ch(1, "A"), ch(2, "B"), ch(3, "A", reliability = 1), ch(4, "C"))
        )
        assertEquals(listOf("A", "B", "C"), merged.map { it.cleanName })
        assertEquals(3L, merged.first().id)
    }

    @Test
    fun equalReliabilityKeepsTheEarlierCopy() {
        val merged = DuplicateMerger.merge(listOf(ch(1, "A"), ch(2, "A")))
        assertEquals(1L, merged.single().id)
    }
}
