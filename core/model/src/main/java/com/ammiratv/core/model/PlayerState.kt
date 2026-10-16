package com.ammiratv.core.model

/**
 * Represents the current state of the video player.
 * Used by PlayerEngine abstraction across different player implementations.
 */
data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isError: Boolean = false,
    /** A network error was hit and the player is retrying by itself (no failure shown yet). */
    val isReconnecting: Boolean = false,
    val errorMessage: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrack: Int = -1,
    val selectedSubtitleTrack: Int = -1,
    val aspectRatio: AspectRatio = AspectRatio.FIT,
    /** Device throughput estimated by the player, bits/s; 0 until known. */
    val bandwidthBps: Long = 0L,
    /** Bitrate of the video being decoded, bits/s; 0 when the stream doesn't declare it. */
    val videoBitrateBps: Long = 0L,
    val droppedFrames: Int = 0,
    /** Decoder in use and picture size ("H264 1920x1080 · c2.qti.avc.decoder"), for diagnostics. */
    val videoInfo: String? = null,
    /** True once the first picture was really drawn; false while the screen would still be black. */
    val firstFrameRendered: Boolean = false
)

data class TrackInfo(
    val index: Int,
    val name: String,
    val language: String? = null,
    val isSelected: Boolean = false
)

enum class AspectRatio {
    FIT,        // Fit within bounds, maintain aspect ratio
    FILL,       // Fill bounds, may crop
    ZOOM,       // 16:9 zoom
    STRETCH,    // Stretch to fill (ignores aspect ratio)
    FOUR_THREE, // 4:3
    ORIGINAL    // Original aspect ratio
}
