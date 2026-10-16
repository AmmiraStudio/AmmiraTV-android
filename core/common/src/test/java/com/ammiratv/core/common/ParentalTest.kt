package com.ammiratv.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalTest {

    @Test
    fun pinMatchesOnlyItself() {
        val stored = PinHasher.hash("1234")
        assertTrue(PinHasher.matches("1234", stored))
        assertFalse(PinHasher.matches("1235", stored))
    }

    @Test
    fun emptyStoredHashNeverMatches() {
        assertFalse(PinHasher.matches("", ""))
    }

    @Test
    fun hashDoesNotContainPin() {
        assertNotEquals("1234", PinHasher.hash("1234"))
        assertEquals(64, PinHasher.hash("1234").length)
    }

    @Test
    fun adultMarkersAreDetected() {
        assertTrue(AdultContentFilter.isAdult("XXX Movies"))
        assertTrue(AdultContentFilter.isAdult("Adulti 18+"))
        assertTrue(AdultContentFilter.isAdult(null, "Playboy TV"))
    }

    @Test
    fun normalNamesAreNotFlagged() {
        assertFalse(AdultContentFilter.isAdult("Sky Sport", "Rai 1"))
        assertFalse(AdultContentFilter.isAdult("Essex News"))
        assertFalse(AdultContentFilter.isAdult(null, null))
    }
}
