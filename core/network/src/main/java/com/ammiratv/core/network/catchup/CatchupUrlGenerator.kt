package com.ammiratv.core.network.catchup

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates catch-up / timeshift playback URLs for IPTV streams.
 * Supports both Xtream Codes timeshift format and M3U catchup templates
 * (Flussonic, Shift, Archive, etc.).
 */
object CatchupUrlGenerator {

    /**
     * Builds an Xtream Codes timeshift URL.
     * Format: {baseUrl}/timeshift/{username}/{password}/{durationMinutes}/{yyyy-MM-dd:HH-mm}/{streamId}.ts
     */
    fun buildXtreamTimeshiftUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: Int,
        startTimeEpochMs: Long,
        endTimeEpochMs: Long
    ): String {
        var base = serverUrl.trim()
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://$base"
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length - 1)
        }

        val durationMinutes = ((endTimeEpochMs - startTimeEpochMs) / (60 * 1000L)).coerceAtLeast(1)

        val sdf = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startFormatted = sdf.format(Date(startTimeEpochMs))

        return "$base/timeshift/$username/$password/$durationMinutes/$startFormatted/$streamId.ts"
    }

    /**
     * Resolves an M3U catchup template by replacing standard placeholders:
     * - {utc} / ${start}: start timestamp in epoch seconds
     * - {lutc} / {end}: end timestamp in epoch seconds
     * - {duration}: duration in seconds
     * - {offset}: seconds from start to current time
     * - {Y}, {m}, {d}, {H}, {M}, {S}: year, month, day, hour, minute, second (UTC)
     */
    fun resolveM3uCatchupUrl(
        catchupSourceTemplate: String,
        startTimeEpochMs: Long,
        endTimeEpochMs: Long,
        streamUrl: String = ""
    ): String {
        val startSec = startTimeEpochMs / 1000L
        val endSec = endTimeEpochMs / 1000L
        val durationSec = (endSec - startSec).coerceAtLeast(1)
        val nowSec = System.currentTimeMillis() / 1000L
        val offsetSec = (nowSec - startSec).coerceAtLeast(0)

        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = startTimeEpochMs
        }
        val year = String.format(Locale.US, "%04d", cal.get(java.util.Calendar.YEAR))
        val month = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.MONTH) + 1)
        val day = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.DAY_OF_MONTH))
        val hour = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.HOUR_OF_DAY))
        val minute = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.MINUTE))
        val second = String.format(Locale.US, "%02d", cal.get(java.util.Calendar.SECOND))

        var resolved = if (catchupSourceTemplate.isNotBlank()) {
            catchupSourceTemplate
        } else {
            // Default appending if no explicit catchup-source
            if (streamUrl.contains("?")) "$streamUrl&utc={utc}&lutc={lutc}" else "$streamUrl?utc={utc}&lutc={lutc}"
        }

        resolved = resolved
            .replace("{utc}", startSec.toString())
            .replace("\${start}", startSec.toString())
            .replace("{lutc}", endSec.toString())
            .replace("{end}", endSec.toString())
            .replace("{duration}", durationSec.toString())
            .replace("{offset}", offsetSec.toString())
            .replace("{Y}", year)
            .replace("{m}", month)
            .replace("{d}", day)
            .replace("{H}", hour)
            .replace("{M}", minute)
            .replace("{S}", second)

        return resolved
    }
}
