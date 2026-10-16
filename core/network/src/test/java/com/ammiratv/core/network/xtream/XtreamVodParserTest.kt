package com.ammiratv.core.network.xtream

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class XtreamVodParserTest {

    private fun parse(json: String) = Json.parseToJsonElement(json)

    @Test
    fun parsesMoviesWithStringNumbers() {
        val items = XtreamVodParser.parseMovies(
            parse(
                """[{"stream_id":"101","name":"Film A","stream_icon":"http://i/a.jpg",
                    "category_id":"7","rating":"7.5","container_extension":"mkv","added":"1700000000"},
                   {"stream_id":102,"name":"Film B"}]"""
            ),
            playlistId = 3
        )

        assertThat(items).hasSize(2)
        assertThat(items[0].id).isEqualTo(101)
        assertThat(items[0].containerExtension).isEqualTo("mkv")
        assertThat(items[0].categoryId).isEqualTo("7")
        assertThat(items[1].containerExtension).isEqualTo("mp4") // default
        assertThat(items[1].playlistId).isEqualTo(3)
    }

    @Test
    fun skipsEntriesWithoutIdOrName() {
        val items = XtreamVodParser.parseMovies(parse("""[{"name":"no id"},{"stream_id":1}]"""), 1)
        assertThat(items).isEmpty()
    }

    @Test
    fun seriesInfoGroupsAndSortsEpisodes() {
        val details = XtreamVodParser.parseSeriesInfo(
            parse(
                """{"info":{"plot":"Trama","youtube_trailer":"https://www.youtube.com/watch?v=dQw4w9WgXcQ"},
                    "episodes":{
                      "2":[{"id":"22","episode_num":1,"title":"S2E1","season":2,"container_extension":"mkv"}],
                      "1":[{"id":"12","episode_num":2,"title":"S1E2","season":1},
                           {"id":"11","episode_num":1,"title":"S1E1","season":1,"info":{"duration_secs":"2700"}}]
                    }}"""
            )
        )

        assertThat(details.details.plot).isEqualTo("Trama")
        assertThat(details.details.trailerYoutubeId).isEqualTo("dQw4w9WgXcQ")
        assertThat(details.seasons.keys.toList()).containsExactly(1, 2).inOrder()
        assertThat(details.seasons.getValue(1).map { it.id }).containsExactly(11L, 12L).inOrder()
        assertThat(details.seasons.getValue(1)[0].durationSecs).isEqualTo(2700)
        assertThat(details.seasons.getValue(2)[0].containerExtension).isEqualTo("mkv")
    }

    @Test
    fun emptyEpisodesAsArrayAndInfoAsArrayDoNotCrash() {
        val details = XtreamVodParser.parseSeriesInfo(parse("""{"info":[],"episodes":[]}"""))
        assertThat(details.seasons).isEmpty()
        assertThat(details.details.plot).isNull()
    }

    @Test
    fun youtubeIdAcceptsBareIdAndUrls() {
        assertThat(XtreamVodParser.youtubeId("dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(XtreamVodParser.youtubeId("https://youtu.be/dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(XtreamVodParser.youtubeId("not a trailer")).isNull()
    }
}
