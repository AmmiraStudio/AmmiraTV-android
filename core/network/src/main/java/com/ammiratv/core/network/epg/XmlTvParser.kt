package com.ammiratv.core.network.epg

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

data class ParsedEpgProgram(
    val channelTvgId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val category: String? = null,
    val iconUrl: String? = null
)

/**
 * Memory-efficient streaming parser for XMLTV EPG data.
 *
 * Automatically detects and decompresses GZIP streams (.xml.gz).
 * Uses Android's XmlPullParser to parse gigabyte-sized EPG feeds with minimal memory usage.
 */
object XmlTvParser {

    /**
     * Parses an XMLTV input stream into a lazy Kotlin Sequence.
     */
    fun parse(inputStream: InputStream): Sequence<ParsedEpgProgram> = sequence {
        val stream = wrapIfGzip(inputStream)
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType

        var currentChannel: String? = null
        var currentStart: Long? = null
        var currentStop: Long? = null
        var currentTitle: String? = null
        var currentDesc: String? = null
        var currentCategory: String? = null
        var currentIcon: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "programme" -> {
                            currentChannel = parser.getAttributeValue(null, "channel")
                            currentStart = XmlTvDateParser.parse(parser.getAttributeValue(null, "start"))
                            currentStop = XmlTvDateParser.parse(parser.getAttributeValue(null, "stop"))
                            currentTitle = null
                            currentDesc = null
                            currentCategory = null
                            currentIcon = null
                        }
                        "title" -> {
                            if (currentTitle == null) {
                                currentTitle = parser.nextText()
                            }
                        }
                        "desc" -> {
                            if (currentDesc == null) {
                                currentDesc = parser.nextText()
                            }
                        }
                        "category" -> {
                            if (currentCategory == null) {
                                currentCategory = parser.nextText()
                            }
                        }
                        "icon" -> {
                            if (currentIcon == null) {
                                currentIcon = parser.getAttributeValue(null, "src")
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("programme", ignoreCase = true)) {
                        val channelId = currentChannel?.trim()
                        val title = currentTitle?.trim()
                        val start = currentStart
                        val stop = currentStop

                        if (!channelId.isNullOrEmpty() && !title.isNullOrEmpty() && start != null && stop != null && stop > start) {
                            yield(
                                ParsedEpgProgram(
                                    channelTvgId = channelId,
                                    title = title,
                                    description = currentDesc?.trim()?.takeIf { it.isNotEmpty() },
                                    startTime = start,
                                    endTime = stop,
                                    category = currentCategory?.trim()?.takeIf { it.isNotEmpty() },
                                    iconUrl = currentIcon?.trim()?.takeIf { it.isNotEmpty() }
                                )
                            )
                        }

                        currentChannel = null
                        currentStart = null
                        currentStop = null
                        currentTitle = null
                        currentDesc = null
                        currentCategory = null
                        currentIcon = null
                    }
                }
            }
            eventType = parser.next()
        }
    }

    /**
     * Inspects magic bytes (0x1F, 0x8B) to wrap in GZIPInputStream if compressed.
     */
    private fun wrapIfGzip(raw: InputStream): InputStream {
        val buffered = if (raw is BufferedInputStream) raw else BufferedInputStream(raw, 8192)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()

        return if (b1 == 0x1f && b2 == 0x8b) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }
    }
}
