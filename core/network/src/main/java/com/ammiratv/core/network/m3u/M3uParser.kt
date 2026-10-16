package com.ammiratv.core.network.m3u

import com.ammiratv.core.common.ChannelNameCleaner
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Parsed channel record extracted from an M3U / M3U8 stream.
 */
data class ParsedM3uEntry(
    val rawName: String,
    val cleanName: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgShift: String? = null,
    val catchup: String? = null,
    val catchupDays: Int? = null,
    val qualityBadge: String? = null
)

/**
 * High-performance streaming M3U parser.
 *
 * Reads an M3U file/stream line-by-line without loading the entire playlist into memory,
 * allowing it to efficiently process massive playlists (50,000+ entries) with minimal RAM.
 */
object M3uParser {

    private const val EXT_INF_PREFIX = "#EXTINF:"
    private const val EXT_GRP_PREFIX = "#EXTGRP:"
    private const val EXT_M3U_PREFIX = "#EXTM3U"

    // Header attribute pointing to the XMLTV guide: #EXTM3U url-tvg="http://..." (or x-tvg-url)
    private val ATTR_URL_TVG = Pattern.compile("(?:url-tvg|x-tvg-url)=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)

    // Regex patterns for attribute extraction
    private val ATTR_TVG_ID = Pattern.compile("tvg-id=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
    private val ATTR_TVG_NAME = Pattern.compile("tvg-name=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
    private val ATTR_TVG_LOGO = Pattern.compile("tvg-logo=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
    private val ATTR_GROUP_TITLE = Pattern.compile("group-title=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
    private val ATTR_TVG_SHIFT = Pattern.compile("tvg-shift=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
    private val ATTR_CATCHUP = Pattern.compile("catchup=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
    private val ATTR_CATCHUP_DAYS = Pattern.compile("catchup-days=[\"']?([0-9]+)[\"']?", Pattern.CASE_INSENSITIVE)

    /**
     * Parses an input stream as a lazy Kotlin Sequence of entries.
     */
    fun parse(inputStream: InputStream): Sequence<ParsedM3uEntry> {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 16384)
        return parse(reader)
    }

    /**
     * Parses from a BufferedReader as a lazy Sequence.
     *
     * @param onEpgUrl invoked (while the sequence is consumed) when the `#EXTM3U` header
     * declares a guide URL. Several comma-separated URLs are passed through untouched.
     */
    fun parse(
        reader: BufferedReader,
        onEpgUrl: (String) -> Unit = {}
    ): Sequence<ParsedM3uEntry> = sequence {
        var currentTvgId: String? = null
        var currentTvgName: String? = null
        var currentTvgLogo: String? = null
        var currentGroupTitle: String? = null
        var currentTvgShift: String? = null
        var currentCatchup: String? = null
        var currentCatchupDays: Int? = null
        var currentRawName: String? = null

        var hasExtInf = false

        while (true) {
            val line = reader.readLine() ?: break
            val trimmed = line.trim()

            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith(EXT_INF_PREFIX, ignoreCase = true)) {
                hasExtInf = true
                val infContent = trimmed.substring(EXT_INF_PREFIX.length).trim()

                // Attributes are before the comma, channel title is after the comma
                val commaIndex = infContent.lastIndexOf(',')
                val attrPart = if (commaIndex != -1) infContent.substring(0, commaIndex) else infContent
                val titlePart = if (commaIndex != -1) infContent.substring(commaIndex + 1).trim() else ""

                currentRawName = if (titlePart.isNotBlank()) titlePart else "Channel"

                currentTvgId = extractAttribute(ATTR_TVG_ID, attrPart)
                currentTvgName = extractAttribute(ATTR_TVG_NAME, attrPart)
                currentTvgLogo = extractAttribute(ATTR_TVG_LOGO, attrPart)
                currentGroupTitle = extractAttribute(ATTR_GROUP_TITLE, attrPart)
                currentTvgShift = extractAttribute(ATTR_TVG_SHIFT, attrPart)
                currentCatchup = extractAttribute(ATTR_CATCHUP, attrPart)
                currentCatchupDays = extractAttribute(ATTR_CATCHUP_DAYS, attrPart)?.toIntOrNull()

            } else if (trimmed.startsWith(EXT_M3U_PREFIX, ignoreCase = true)) {
                extractAttribute(ATTR_URL_TVG, trimmed)?.takeIf { it.isNotBlank() }?.let(onEpgUrl)
            } else if (trimmed.startsWith(EXT_GRP_PREFIX, ignoreCase = true)) {
                // Secondary group specification
                val grp = trimmed.substring(EXT_GRP_PREFIX.length).trim()
                if (grp.isNotEmpty()) {
                    currentGroupTitle = grp
                }
            } else if (!trimmed.startsWith("#")) {
                // This is a stream URL
                if (hasExtInf && currentRawName != null) {
                    val rawName = currentRawName
                    val cleanName = ChannelNameCleaner.clean(rawName)
                    val quality = ChannelNameCleaner.extractQuality(rawName)

                    yield(
                        ParsedM3uEntry(
                            rawName = rawName,
                            cleanName = cleanName,
                            streamUrl = trimmed,
                            logoUrl = currentTvgLogo?.takeIf { it.isNotBlank() },
                            groupTitle = currentGroupTitle?.takeIf { it.isNotBlank() },
                            tvgId = currentTvgId?.takeIf { it.isNotBlank() },
                            tvgName = currentTvgName?.takeIf { it.isNotBlank() },
                            tvgShift = currentTvgShift?.takeIf { it.isNotBlank() },
                            catchup = currentCatchup?.takeIf { it.isNotBlank() },
                            catchupDays = currentCatchupDays,
                            qualityBadge = quality
                        )
                    )

                    // Reset state for next entry
                    hasExtInf = false
                    currentRawName = null
                    currentTvgId = null
                    currentTvgName = null
                    currentTvgLogo = null
                    currentGroupTitle = null
                    currentTvgShift = null
                    currentCatchup = null
                    currentCatchupDays = null
                }
            }
        }
    }

    /**
     * Reads the playlist in chunks and executes the onChunk callback.
     * Ideal for Room batch insertions so memory usage remains constant.
     */
    inline fun parseInChunks(
        reader: BufferedReader,
        chunkSize: Int = 500,
        crossinline onChunk: (List<ParsedM3uEntry>) -> Unit
    ): Int {
        var totalCount = 0
        val buffer = ArrayList<ParsedM3uEntry>(chunkSize)

        for (entry in parse(reader)) {
            buffer.add(entry)
            totalCount++
            if (buffer.size >= chunkSize) {
                onChunk(buffer)
                buffer.clear()
            }
        }

        if (buffer.isNotEmpty()) {
            onChunk(buffer)
            buffer.clear()
        }

        return totalCount
    }

    private fun extractAttribute(pattern: Pattern, text: String): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)?.trim()
        } else {
            null
        }
    }
}
