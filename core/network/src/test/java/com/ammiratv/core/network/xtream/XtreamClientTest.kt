package com.ammiratv.core.network.xtream

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test
import retrofit2.Retrofit

class XtreamClientTest {

    private val retrofit = mockk<Retrofit>(relaxed = true)
    private val client = XtreamClient(retrofit)

    @Test
    fun testBuildLiveStreamUrl() {
        val url = client.buildLiveStreamUrl(
            serverUrl = "http://iptv.example.com:8080",
            user = "testuser",
            pass = "testpass",
            streamId = 12345,
            extension = "m3u8"
        )
        assertThat(url).isEqualTo("http://iptv.example.com:8080/live/testuser/testpass/12345.m3u8")
    }

    @Test
    fun testBuildLiveStreamUrlWithTrailingSlash() {
        val url = client.buildLiveStreamUrl(
            serverUrl = "http://iptv.example.com:8080/",
            user = "myuser",
            pass = "mypass",
            streamId = 999
        )
        assertThat(url).isEqualTo("http://iptv.example.com:8080/live/myuser/mypass/999.ts")
    }
}
