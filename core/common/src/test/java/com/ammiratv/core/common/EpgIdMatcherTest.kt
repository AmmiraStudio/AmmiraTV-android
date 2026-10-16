package com.ammiratv.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgIdMatcherTest {

    private val ids = listOf("Rai1.it", "Rai2.it", "SkySport1.it@SD", "SkySport24.it", "Canale5.it", "La7.it")

    @Test
    fun normalizesNamesAndIdsToTheSameKey() {
        assertEquals("rai1", EpgIdMatcher.normalize("IT: Rai 1 HD"))
        assertEquals("rai1", EpgIdMatcher.normalize("Rai1.it"))
        assertEquals("skysport1", EpgIdMatcher.normalize("Sky Sport 1 FHD"))
        assertEquals("skysport1", EpgIdMatcher.normalize("SkySport1.it@SD"))
    }

    @Test
    fun exactNormalizedMatchWins() {
        assertEquals("Rai1.it", EpgIdMatcher.bestMatch("Rai 1", ids))
        assertEquals("SkySport1.it@SD", EpgIdMatcher.bestMatch("|IT| Sky Sport 1 4K", ids))
        assertEquals("Canale5.it", EpgIdMatcher.bestMatch("Canale 5 HD", ids))
    }

    @Test
    fun numbersDistinguishNeighbours() {
        // "rai1" and "rai2" are one edit apart but too short to be fuzzy-matched
        assertEquals("Rai2.it", EpgIdMatcher.bestMatch("Rai 2", ids))
        assertNull(EpgIdMatcher.bestMatch("Rai 3", ids))
    }

    @Test
    fun fuzzyMatchNeedsASingleCandidate() {
        // "canale5" vs a typo'd playlist name "canalle 5"
        assertEquals("Canale5.it", EpgIdMatcher.bestMatch("Canalle 5", ids))

        val ambiguous = listOf("skysportuno.it", "skysportund.it")
        assertNull(EpgIdMatcher.bestMatch("Sky Sport Unx", ambiguous))
    }

    @Test
    fun unknownChannelHasNoMatch() {
        assertNull(EpgIdMatcher.bestMatch("Completely Different Channel", ids))
        assertNull(EpgIdMatcher.bestMatch("", ids))
    }

    @Test
    fun editDistanceHelper() {
        assertTrue(EpgIdMatcher.withinOneEdit("canale5", "canalle5"))
        assertTrue(EpgIdMatcher.withinOneEdit("canale5", "canale6"))
        assertFalse(EpgIdMatcher.withinOneEdit("canale5", "canale55x"))
    }
}
