package com.ammiratv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonightRecommenderTest {

    private val now = 1_800_000_000_000L
    private val hour = 3600 * 1000L

    private fun program(
        channel: String,
        title: String,
        startsIn: Long,
        lasts: Long = hour,
        category: String? = null
    ) = EpgProgram(
        channelTvgId = channel,
        title = title,
        startTime = now + startsIn,
        endTime = now + startsIn + lasts,
        category = category
    )

    @Test
    fun onlyRuleMatchesAreRecommended() {
        val result = TonightRecommender.rank(
            programs = listOf(
                program("fav", "Film del cuore", 2 * hour),
                program("other", "Telegiornale", 1 * hour)
            ),
            favoriteChannelIds = setOf("fav"),
            keywords = emptyList(),
            nowMs = now
        )
        assertEquals(listOf("Film del cuore"), result.map { it.program.title })
    }

    @Test
    fun keywordFindsProgrammesOnAnyChannelEvenDaysAhead() {
        val result = TonightRecommender.rank(
            programs = listOf(program("sport", "Juventus - Milan", 3 * 24 * hour)),
            favoriteChannelIds = emptySet(),
            keywords = listOf("juventus"),
            nowMs = now
        )
        assertEquals(1, result.size)
        assertTrue(result[0].reasons.first().contains("juventus"))
    }

    @Test
    fun keywordPlusFavouriteOutranksFavouriteAlone() {
        val result = TonightRecommender.rank(
            programs = listOf(
                program("fav", "Quiz serale", 2 * hour),
                program("fav", "Formula 1 Gran Premio", 2 * hour + 1)
            ),
            favoriteChannelIds = setOf("fav"),
            keywords = listOf("formula 1"),
            nowMs = now
        )
        assertEquals("Formula 1 Gran Premio", result.first().program.title)
    }

    @Test
    fun finishedProgrammesAreDropped() {
        val result = TonightRecommender.rank(
            programs = listOf(program("fav", "Già finito", startsIn = -3 * hour, lasts = hour)),
            favoriteChannelIds = setOf("fav"),
            keywords = emptyList(),
            nowMs = now
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun airingNowGetsABoostOverLater() {
        val result = TonightRecommender.rank(
            programs = listOf(
                program("fav", "Più tardi", 5 * hour),
                program("fav", "Adesso", -hour / 2, lasts = hour)
            ),
            favoriteChannelIds = setOf("fav"),
            keywords = emptyList(),
            nowMs = now
        )
        assertEquals("Adesso", result.first().program.title)
        assertTrue(result.first().reasons.contains("In onda ora"))
    }
}
