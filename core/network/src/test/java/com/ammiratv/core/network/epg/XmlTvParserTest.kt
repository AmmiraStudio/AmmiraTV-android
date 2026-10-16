package com.ammiratv.core.network.epg

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

class XmlTvParserTest {

    @Test
    fun testParseXmlTv() {
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="Rai1.it">
                <display-name>Rai 1</display-name>
              </channel>
              <programme start="20261014200000 +0200" stop="20261014203000 +0200" channel="Rai1.it">
                <title>TG1</title>
                <desc>Telegiornale delle ore 20:00</desc>
                <category>News</category>
              </programme>
              <programme start="20261014203000 +0200" stop="20261014213000 +0200" channel="Rai1.it">
                <title>Affari Tuoi</title>
                <desc>Gioco dei pacchi condotto in prima serata</desc>
                <category>Game Show</category>
                <icon src="https://example.com/affarituoi.jpg" />
              </programme>
            </tv>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(xmlContent.toByteArray(StandardCharsets.UTF_8))
        val programs = XmlTvParser.parse(inputStream).toList()

        assertThat(programs).hasSize(2)

        val p1 = programs[0]
        assertThat(p1.channelTvgId).isEqualTo("Rai1.it")
        assertThat(p1.title).isEqualTo("TG1")
        assertThat(p1.description).isEqualTo("Telegiornale delle ore 20:00")
        assertThat(p1.category).isEqualTo("News")

        val p2 = programs[1]
        assertThat(p2.channelTvgId).isEqualTo("Rai1.it")
        assertThat(p2.title).isEqualTo("Affari Tuoi")
        assertThat(p2.category).isEqualTo("Game Show")
        assertThat(p2.iconUrl).isEqualTo("https://example.com/affarituoi.jpg")
        assertThat(p2.startTime).isGreaterThan(p1.startTime)
    }

    @Test
    fun testParseGzippedXmlTv() {
        val xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="20261014210000 +0000" stop="20261014220000 +0000" channel="Canale5.it">
                <title>Striscia la Notizia</title>
                <desc>Tg satirico</desc>
              </programme>
            </tv>
        """.trimIndent()

        // Gzip the XML content into memory
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { gz ->
            gz.write(xmlContent.toByteArray(StandardCharsets.UTF_8))
        }

        val gzippedStream = ByteArrayInputStream(byteOut.toByteArray())
        val programs = XmlTvParser.parse(gzippedStream).toList()

        assertThat(programs).hasSize(1)
        assertThat(programs[0].channelTvgId).isEqualTo("Canale5.it")
        assertThat(programs[0].title).isEqualTo("Striscia la Notizia")
        assertThat(programs[0].description).isEqualTo("Tg satirico")
    }

    @Test
    fun testDateParser() {
        val ts = XmlTvDateParser.parse("20261014200000 +0200")
        assertThat(ts).isNotNull()

        val tsUtc = XmlTvDateParser.parse("20261014200000Z")
        assertThat(tsUtc).isNotNull()
    }
}
