package com.ammiratv.core.network.epg

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Utility for parsing XMLTV date strings into Unix timestamps (milliseconds).
 * Standard XMLTV date format: "yyyyMMddHHmmss Z" (e.g., "20261014200000 +0200")
 * Also handles formats without timezone, "Z" suffix, or missing seconds.
 */
object XmlTvDateParser {

    private val FORMAT_WITH_TZ = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val FORMAT_NO_TZ = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val FORMAT_SHORT = SimpleDateFormat("yyyyMMddHHmm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @Synchronized
    fun parse(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val clean = dateStr.trim()

        return try {
            if (clean.contains(" ") || clean.contains("+") || clean.contains("-")) {
                // Example: "20261014200000 +0200"
                // Standardize "+02:00" to "+0200" if colon is present in timezone
                val normalized = if (clean.length >= 5 && clean[clean.length - 3] == ':') {
                    clean.substring(0, clean.length - 3) + clean.substring(clean.length - 2)
                } else {
                    clean
                }
                FORMAT_WITH_TZ.parse(normalized)?.time
            } else if (clean.endsWith("Z", ignoreCase = true)) {
                val noZ = clean.substring(0, clean.length - 1)
                FORMAT_NO_TZ.parse(noZ)?.time
            } else if (clean.length == 14) {
                FORMAT_NO_TZ.parse(clean)?.time
            } else if (clean.length == 12) {
                FORMAT_SHORT.parse(clean)?.time
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
