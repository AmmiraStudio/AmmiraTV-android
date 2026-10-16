package com.ammiratv.core.network.m3u

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class M3uParserTest {

    @Test
    fun testParseStandardM3u() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="Rai1.it" tvg-name="Rai 1" tvg-logo="https://example.com/rai1.png" group-title="ITALIA DTT",IT: Rai 1 HD
            http://stream.example.com/live/rai1.m3u8
            #EXTINF:-1 tvg-id="Canale5.it" tvg-logo="https://example.com/c5.png" group-title="ITALIA DTT",IT | Canale 5 FHD [1080p]
            http://stream.example.com/live/canale5.m3u8
        """.trimIndent()

        val entries = M3uParser.parse(BufferedReader(StringReader(m3uContent))).toList()

        assertThat(entries).hasSize(2)

        val entry1 = entries[0]
        assertThat(entry1.rawName).isEqualTo("IT: Rai 1 HD")
        assertThat(entry1.cleanName).isEqualTo("Rai 1")
        assertThat(entry1.tvgId).isEqualTo("Rai1.it")
        assertThat(entry1.tvgName).isEqualTo("Rai 1")
        assertThat(entry1.logoUrl).isEqualTo("https://example.com/rai1.png")
        assertThat(entry1.groupTitle).isEqualTo("ITALIA DTT")
        assertThat(entry1.streamUrl).isEqualTo("http://stream.example.com/live/rai1.m3u8")
        assertThat(entry1.qualityBadge).isEqualTo("HD")

        val entry2 = entries[1]
        assertThat(entry2.rawName).isEqualTo("IT | Canale 5 FHD [1080p]")
        assertThat(entry2.cleanName).isEqualTo("Canale 5")
        assertThat(entry2.tvgId).isEqualTo("Canale5.it")
        assertThat(entry2.groupTitle).isEqualTo("ITALIA DTT")
        assertThat(entry2.streamUrl).isEqualTo("http://stream.example.com/live/canale5.m3u8")
        assertThat(entry2.qualityBadge).isEqualTo("FHD")
    }

    @Test
    fun testParseWithExtGrp() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="SkySport1.it",Sky Sport Uno HD
            #EXTGRP:SPORT
            http://stream.example.com/live/skysport.m3u8
        """.trimIndent()

        val entries = M3uParser.parse(BufferedReader(StringReader(m3uContent))).toList()

        assertThat(entries).hasSize(1)
        assertThat(entries[0].cleanName).isEqualTo("Sky Sport Uno")
        assertThat(entries[0].groupTitle).isEqualTo("SPORT")
    }

    @Test
    fun testHeaderExposesGuideUrl() {
        val m3uContent = """
            #EXTM3U url-tvg="http://epg.example.com/guide.xml.gz"
            #EXTINF:-1 tvg-id="a",Channel A
            http://stream.example.com/a.m3u8
        """.trimIndent()

        var epgUrl: String? = null
        val entries = M3uParser.parse(BufferedReader(StringReader(m3uContent))) { epgUrl = it }.toList()

        assertThat(entries).hasSize(1)
        assertThat(epgUrl).isEqualTo("http://epg.example.com/guide.xml.gz")
    }

    @Test
    fun testHeaderWithoutGuideUrlDoesNotInvokeCallback() {
        val m3uContent = "#EXTM3U\n#EXTINF:-1,A\nhttp://x/a.m3u8"

        var called = false
        M3uParser.parse(BufferedReader(StringReader(m3uContent))) { called = true }.toList()

        assertThat(called).isFalse()
    }

    @Test
    fun testParseInChunks() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1,Channel 1
            http://stream.example.com/1.m3u8
            #EXTINF:-1,Channel 2
            http://stream.example.com/2.m3u8
            #EXTINF:-1,Channel 3
            http://stream.example.com/3.m3u8
            #EXTINF:-1,Channel 4
            http://stream.example.com/4.m3u8
            #EXTINF:-1,Channel 5
            http://stream.example.com/5.m3u8
        """.trimIndent()

        val chunks = mutableListOf<List<ParsedM3uEntry>>()
        val total = M3uParser.parseInChunks(BufferedReader(StringReader(m3uContent)), chunkSize = 2) { chunk ->
            chunks.add(chunk.toList())
        }

        assertThat(total).isEqualTo(5)
        assertThat(chunks).hasSize(3) // Chunks of 2, 2, 1
        assertThat(chunks[0]).hasSize(2)
        assertThat(chunks[1]).hasSize(2)
        assertThat(chunks[2]).hasSize(1)
    }
}
