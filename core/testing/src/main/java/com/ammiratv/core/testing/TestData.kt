package com.ammiratv.core.testing

import com.ammiratv.core.model.Category
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.model.StreamType

/**
 * Shared test data for unit and integration tests.
 */
object TestData {

    val samplePlaylist = Playlist(
        id = 1,
        name = "Test Playlist",
        url = "http://example.com/playlist.m3u",
        type = PlaylistType.M3U
    )

    val sampleCategory = Category(
        id = 1,
        playlistId = 1,
        name = "Entertainment",
        type = StreamType.LIVE
    )

    val sampleChannels = listOf(
        Channel(
            id = 1, playlistId = 1, categoryId = 1,
            name = "Test Channel 1", streamUrl = "http://example.com/stream1.m3u8",
            tvgId = "test1"
        ),
        Channel(
            id = 2, playlistId = 1, categoryId = 1,
            name = "Test Channel 2", streamUrl = "http://example.com/stream2.m3u8",
            tvgId = "test2"
        ),
        Channel(
            id = 3, playlistId = 1, categoryId = 1,
            name = "Test Channel 3", streamUrl = "http://example.com/stream3.m3u8",
            tvgId = "test3"
        )
    )

    val sampleProgram = EpgProgram(
        id = 1,
        epgSourceId = 1,
        channelTvgId = "test1",
        title = "Test Program",
        description = "A test program description",
        startTime = System.currentTimeMillis(),
        endTime = System.currentTimeMillis() + 3600_000
    )
}
