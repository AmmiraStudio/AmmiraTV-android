package com.ammiratv.core.model

import java.util.Calendar
import java.util.TimeZone

enum class PlaybackEventType { START_OK, START_FAILED, REBUFFER, STALL }

data class PlaybackEvent(
    val playlistId: Long,
    val channelId: Long,
    val timestamp: Long,
    val type: PlaybackEventType,
    val durationMs: Long = 0
)

/** Provider report card computed from recent [PlaybackEvent]s. */
data class ProviderReport(
    val sessions: Int,
    /** 0..1: playbacks that actually started. */
    val startSuccessRate: Float,
    val averageStartMs: Long,
    val rebuffers: Int,
    val stalls: Int,
    /** Hours of the day (0..23) with the most trouble, worst first. */
    val worstHours: List<Int>
) {
    val hasData: Boolean get() = sessions > 0
}

object ProviderReportCalculator {

    /** Hours with fewer sessions than this are too noisy to be called "worst". */
    private const val MIN_SESSIONS_PER_HOUR = 2

    fun calculate(events: List<PlaybackEvent>, zone: TimeZone = TimeZone.getDefault()): ProviderReport {
        val starts = events.filter { it.type == PlaybackEventType.START_OK || it.type == PlaybackEventType.START_FAILED }
        val ok = starts.filter { it.type == PlaybackEventType.START_OK }
        val rebuffers = events.count { it.type == PlaybackEventType.REBUFFER }
        val stalls = events.count { it.type == PlaybackEventType.STALL }

        val sessionsByHour = starts.groupingBy { hourOf(it.timestamp, zone) }.eachCount()
        val problemsByHour = events
            .filter { it.type != PlaybackEventType.START_OK }
            .groupingBy { hourOf(it.timestamp, zone) }.eachCount()

        val worst = problemsByHour
            .filter { (hour, problems) ->
                problems >= 2 && (sessionsByHour[hour] ?: 0) >= MIN_SESSIONS_PER_HOUR
            }
            .entries
            .sortedByDescending { (hour, problems) -> problems.toFloat() / (sessionsByHour[hour] ?: 1) }
            .take(3)
            .map { it.key }

        return ProviderReport(
            sessions = starts.size,
            startSuccessRate = if (starts.isEmpty()) 0f else ok.size.toFloat() / starts.size,
            averageStartMs = if (ok.isEmpty()) 0 else ok.sumOf { it.durationMs } / ok.size,
            rebuffers = rebuffers,
            stalls = stalls,
            worstHours = worst
        )
    }

    fun hourOf(timestamp: Long, zone: TimeZone): Int =
        Calendar.getInstance(zone).apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
}

/** "Buffer che impara": raises the buffer ahead of the hours where this provider usually struggles. */
object BufferAdvisor {
    private const val MAX_BUFFER_MS = 10_000
    private const val TROUBLE_THRESHOLD = 3

    fun recommendedBufferMs(
        events: List<PlaybackEvent>,
        nowMs: Long,
        currentBufferMs: Int,
        zone: TimeZone = TimeZone.getDefault()
    ): Int {
        val hour = ProviderReportCalculator.hourOf(nowMs, zone)
        // Look at this hour and the next: the buffer must be ready *before* the bad stretch.
        val upcoming = setOf(hour, (hour + 1) % 24)
        val trouble = events.count {
            it.type != PlaybackEventType.START_OK &&
                ProviderReportCalculator.hourOf(it.timestamp, zone) in upcoming
        }
        return if (trouble >= TROUBLE_THRESHOLD) {
            (currentBufferMs * 2).coerceAtMost(MAX_BUFFER_MS)
        } else {
            currentBufferMs
        }
    }
}

enum class NetworkVerdictKind { YOUR_CONNECTION, PROVIDER, ALL_GOOD, NOT_ENOUGH_DATA }

/** The "è colpa tua o del provider?" answer. */
object NetworkVerdict {

    /**
     * @param bandwidthBps measured throughput of the device (ExoPlayer's estimate), 0 if unknown
     * @param streamBitrateBps bitrate of the stream being played, 0 if unknown
     * @param problems rebuffers + stalls + failed starts in the observation window
     */
    fun evaluate(bandwidthBps: Long, streamBitrateBps: Long, problems: Int): NetworkVerdictKind {
        if (bandwidthBps <= 0 || streamBitrateBps <= 0) return NetworkVerdictKind.NOT_ENOUGH_DATA
        // Streaming needs headroom: below ~1.5x the stream bitrate the line itself is the limit.
        val lineIsTooSlow = bandwidthBps < streamBitrateBps * 3 / 2
        return when {
            lineIsTooSlow -> NetworkVerdictKind.YOUR_CONNECTION
            problems > 0 -> NetworkVerdictKind.PROVIDER // plenty of bandwidth yet it still stutters
            else -> NetworkVerdictKind.ALL_GOOD
        }
    }
}
