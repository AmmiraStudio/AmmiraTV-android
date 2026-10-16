package com.ammiratv.core.model

import com.ammiratv.core.model.StreamHealthMonitor.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHealthMonitorTest {

    private fun monitor() = StreamHealthMonitor(stallThresholdMs = 10_000).also { it.onNewStream(0) }

    private fun StreamHealthMonitor.tick(
        now: Long,
        playing: Boolean = true,
        buffering: Boolean = false,
        error: Boolean = false,
        position: Long = now
    ) = onState(now, playing, buffering, error, position)

    @Test
    fun reportsTimeToFirstFrame() {
        val m = monitor()
        assertTrue(m.tick(100, playing = false, buffering = true).isEmpty())
        assertEquals(listOf<Signal>(Signal.StartOk(900)), m.tick(900))
        assertTrue(m.tick(1000).isEmpty()) // reported once
    }

    @Test
    fun failedStartIsReportedOnce() {
        val m = monitor()
        assertEquals(listOf<Signal>(Signal.StartFailed), m.tick(500, playing = false, error = true))
        assertTrue(m.tick(600, playing = false, error = true).isEmpty())
    }

    @Test
    fun longBufferingBecomesARebuffer() {
        val m = monitor()
        m.tick(100)
        assertTrue(m.tick(1000, playing = false, buffering = true).isEmpty())
        assertEquals(listOf<Signal>(Signal.Rebuffer(2000)), m.tick(3000, position = 1000))
    }

    @Test
    fun shortBufferingIsIgnored() {
        val m = monitor()
        m.tick(100)
        m.tick(1000, playing = false, buffering = true)
        assertTrue(m.tick(1200, position = 1000).isEmpty())
    }

    @Test
    fun frozenPictureWithoutErrorIsAStall() {
        val m = monitor()
        m.tick(100, position = 100)
        // position stuck at 100 while the player says "playing"
        assertTrue(m.tick(5_000, position = 100).isEmpty())
        assertEquals(listOf<Signal>(Signal.Stalled), m.tick(10_200, position = 100))
        // not reported again until it moves and freezes anew
        assertTrue(m.tick(20_000, position = 100).isEmpty())
    }

    @Test
    fun pauseDoesNotCountAsStall() {
        val m = monitor()
        m.tick(100, position = 100)
        assertTrue(m.tick(60_000, playing = false, position = 100).isEmpty())
        assertTrue(m.tick(60_500, playing = true, position = 100).isEmpty())
    }

    @Test
    fun newStreamResetsEverything() {
        val m = monitor()
        m.tick(100)
        m.onNewStream(1_000)
        assertEquals(listOf<Signal>(Signal.StartOk(500)), m.tick(1_500))
    }
}
