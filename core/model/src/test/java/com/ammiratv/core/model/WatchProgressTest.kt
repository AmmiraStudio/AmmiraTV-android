package com.ammiratv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressTest {

    private val base = WatchProgress(
        profileId = 1, contentKey = WatchProgress.movieKey(2, 3), kind = VodKind.MOVIE,
        playlistId = 2, title = "Film", streamUrl = "http://x/movie.mp4"
    )

    @Test
    fun midMovieKeepsPosition() {
        val p = base.withPosition(positionMs = 600_000, durationMs = 6_000_000, nowMs = 5)
        assertEquals(600_000, p.positionMs)
        assertFalse(p.isWatched)
        assertEquals(0.1f, p.fraction, 0.001f)
    }

    @Test
    fun reachingTheCreditsMarksWatchedAndResets() {
        val p = base.withPosition(positionMs = 5_700_000, durationMs = 6_000_000, nowMs = 5)
        assertTrue(p.isWatched)
        assertEquals(0, p.positionMs)
    }

    @Test
    fun unknownDurationNeverMarksWatched() {
        val p = base.withPosition(positionMs = 9_999_999, durationMs = 0, nowMs = 5)
        assertFalse(p.isWatched)
        assertEquals(0f, p.fraction, 0f)
    }

    @Test
    fun keysAreScopedByPlaylist() {
        assertEquals("movie:2:3", WatchProgress.movieKey(2, 3))
        assertEquals("episode:2:77", WatchProgress.episodeKey(2, 77))
    }
}
