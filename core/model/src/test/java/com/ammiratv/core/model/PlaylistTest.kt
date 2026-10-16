package com.ammiratv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun playlist(expiration: Long?) =
        Playlist(name = "Test", url = "http://x", expirationDate = expiration)

    @Test
    fun noExpiryMeansNull() {
        assertNull(playlist(null).daysUntilExpiry(now))
        assertNull(playlist(0L).daysUntilExpiry(now))
    }

    @Test
    fun countsWholeDaysLeft() {
        assertEquals(12, playlist(now + 12 * day + 1_000).daysUntilExpiry(now))
        assertEquals(0, playlist(now + day - 1_000).daysUntilExpiry(now))
    }

    @Test
    fun expiredIsNegative() {
        assertEquals(-1, playlist(now - 1_000).daysUntilExpiry(now))
        assertEquals(-3, playlist(now - 2 * day - 1_000).daysUntilExpiry(now))
    }
}
