package com.ammiratv.core.network.localimport

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalImportServerTest {

    @Test
    fun parsesM3uForm() {
        val request = LocalImportServer.parseForm("type=M3U&name=Casa&url=http%3A%2F%2Fexample.com%2Fl.m3u")
        assertThat(request).isEqualTo(
            ImportRequest("M3U", "Casa", "http://example.com/l.m3u", "", "")
        )
    }

    @Test
    fun parsesXtreamFormWithEncodedCharacters() {
        val request = LocalImportServer.parseForm(
            "type=XTREAM&name=&url=http%3A%2F%2Fh.example%3A8080&username=u+1&password=p%26w"
        )
        assertThat(request?.type).isEqualTo("XTREAM")
        assertThat(request?.username).isEqualTo("u 1")
        assertThat(request?.password).isEqualTo("p&w")
    }

    @Test
    fun rejectsIncompleteSubmissions() {
        assertThat(LocalImportServer.parseForm("type=M3U&url=")).isNull()
        assertThat(LocalImportServer.parseForm("type=XTREAM&url=http%3A%2F%2Fx&username=a")).isNull()
        assertThat(LocalImportServer.parseForm("type=OTHER&url=http%3A%2F%2Fx")).isNull()
    }

    @Test
    fun tokensAreRandomAndLongEnough() {
        val server = LocalImportServer()
        val a = server.newToken()
        val b = server.newToken()
        assertThat(a).hasLength(18)
        assertThat(a).isNotEqualTo(b)
    }
}
