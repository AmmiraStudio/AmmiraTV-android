package com.ammiratv.core.network.catchup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchupUrlGeneratorTest {

    @Test
    fun testBuildXtreamTimeshiftUrl() {
        val serverUrl = "http://iptv.server.com:8080/"
        val username = "testuser"
        val password = "testpassword"
        val streamId = 4210

        // 2026-10-14 20:30:00 UTC = 1792009800000L
        val startMs = 1792009800000L
        // 90 minutes later (duration = 90)
        val endMs = startMs + (90 * 60 * 1000L)

        val url = CatchupUrlGenerator.buildXtreamTimeshiftUrl(
            serverUrl = serverUrl,
            username = username,
            password = password,
            streamId = streamId,
            startTimeEpochMs = startMs,
            endTimeEpochMs = endMs
        )

        assertEquals(
            "http://iptv.server.com:8080/timeshift/testuser/testpassword/90/2026-10-14:20-30/4210.ts",
            url
        )
    }

    @Test
    fun testResolveM3uCatchupUrlWithPlaceholders() {
        val template = "http://stream.net/live/ch1/timeshift.m3u8?utc={utc}&lutc={lutc}&duration={duration}"
        val startMs = 1700000000_000L
        val endMs = 1700003600_000L // 1 hour duration (3600s)

        val resolved = CatchupUrlGenerator.resolveM3uCatchupUrl(
            catchupSourceTemplate = template,
            startTimeEpochMs = startMs,
            endTimeEpochMs = endMs
        )

        assertEquals(
            "http://stream.net/live/ch1/timeshift.m3u8?utc=1700000000&lutc=1700003600&duration=3600",
            resolved
        )
    }

    @Test
    fun testResolveM3uCatchupUrlFallback() {
        val streamUrl = "http://stream.net/live/ch1.m3u8"
        val startMs = 1700000000_000L
        val endMs = 1700001800_000L

        val resolved = CatchupUrlGenerator.resolveM3uCatchupUrl(
            catchupSourceTemplate = "",
            startTimeEpochMs = startMs,
            endTimeEpochMs = endMs,
            streamUrl = streamUrl
        )

        assertTrue(resolved.contains("utc=1700000000"))
        assertTrue(resolved.contains("lutc=1700001800"))
    }
}
