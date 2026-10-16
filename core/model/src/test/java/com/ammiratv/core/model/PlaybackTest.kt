package com.ammiratv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PlaybackTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(hour: Int): Long = Calendar.getInstance(utc).apply {
        clear(); set(2026, Calendar.OCTOBER, 14, hour, 30, 0)
    }.timeInMillis

    private fun event(type: PlaybackEventType, hour: Int, duration: Long = 0) =
        PlaybackEvent(playlistId = 1, channelId = 1, timestamp = at(hour), type = type, durationMs = duration)

    @Test
    fun reportCountsSuccessAndAverageStart() {
        val report = ProviderReportCalculator.calculate(
            listOf(
                event(PlaybackEventType.START_OK, 20, 800),
                event(PlaybackEventType.START_OK, 20, 1200),
                event(PlaybackEventType.START_FAILED, 20)
            ),
            utc
        )
        assertEquals(3, report.sessions)
        assertEquals(2f / 3f, report.startSuccessRate, 0.001f)
        assertEquals(1000, report.averageStartMs)
    }

    @Test
    fun worstHoursNeedEnoughEvidence() {
        val events = listOf(
            // 21h: 2 sessions, 3 problems -> worst
            event(PlaybackEventType.START_OK, 21, 500),
            event(PlaybackEventType.START_OK, 21, 500),
            event(PlaybackEventType.REBUFFER, 21, 3000),
            event(PlaybackEventType.REBUFFER, 21, 3000),
            event(PlaybackEventType.STALL, 21),
            // 9h: a single failure on a single session -> too little data
            event(PlaybackEventType.START_FAILED, 9)
        )
        val report = ProviderReportCalculator.calculate(events, utc)
        assertEquals(listOf(21), report.worstHours)
        assertEquals(2, report.rebuffers)
        assertEquals(1, report.stalls)
    }

    @Test
    fun emptyReportHasNoData() {
        assertFalse(ProviderReportCalculator.calculate(emptyList(), utc).hasData)
    }

    @Test
    fun bufferGrowsBeforeTheUsualBadHour() {
        val history = List(3) { event(PlaybackEventType.REBUFFER, 21, 2000) }
        // 20:30 now -> the 21h trouble is within the next hour
        assertEquals(4000, BufferAdvisor.recommendedBufferMs(history, at(20), 2000, utc))
        // 12:30 now -> unaffected
        assertEquals(2000, BufferAdvisor.recommendedBufferMs(history, at(12), 2000, utc))
    }

    @Test
    fun bufferIsCapped() {
        val history = List(5) { event(PlaybackEventType.STALL, 21) }
        assertEquals(10_000, BufferAdvisor.recommendedBufferMs(history, at(21), 8000, utc))
    }

    @Test
    fun verdictBlamesTheLineWhenBandwidthIsTight() {
        // 4 Mbps stream on a 5 Mbps line: not enough headroom
        assertEquals(
            NetworkVerdictKind.YOUR_CONNECTION,
            NetworkVerdict.evaluate(bandwidthBps = 5_000_000, streamBitrateBps = 4_000_000, problems = 3)
        )
    }

    @Test
    fun verdictBlamesProviderWhenLineIsFastButStreamStutters() {
        assertEquals(
            NetworkVerdictKind.PROVIDER,
            NetworkVerdict.evaluate(bandwidthBps = 50_000_000, streamBitrateBps = 4_000_000, problems = 2)
        )
        assertEquals(
            NetworkVerdictKind.ALL_GOOD,
            NetworkVerdict.evaluate(bandwidthBps = 50_000_000, streamBitrateBps = 4_000_000, problems = 0)
        )
        assertTrue(
            NetworkVerdict.evaluate(0, 4_000_000, 1) == NetworkVerdictKind.NOT_ENOUGH_DATA
        )
    }
}
