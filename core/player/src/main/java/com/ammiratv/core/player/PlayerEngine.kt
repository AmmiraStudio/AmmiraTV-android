package com.ammiratv.core.player

import com.ammiratv.core.model.AspectRatio
import com.ammiratv.core.model.PlayerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the video player implementation.
 *
 * The first implementation uses Media3/ExoPlayer. This interface allows
 * swapping to libVLC or mpv as fallback players without modifying
 * the rest of the app.
 *
 * Usage:
 *   val player: PlayerEngine = // injected via Hilt
 *   player.prepare(streamUrl)
 *   player.play()
 *   // Observe player.state for UI updates
 */
interface PlayerEngine {

    /** Observable state of the player (playing, buffering, error, position, etc.) */
    val state: StateFlow<PlayerState>

    /** Prepare a stream for playback. Headers can include auth tokens, user-agent, etc. */
    fun prepare(uri: String, headers: Map<String, String> = emptyMap())

    /** Title shown by the system media controls / notification for the next [prepare]. */
    fun setNowPlaying(title: String?, subtitle: String?) {}

    /**
     * The app left the screen and background playback is switched off: pause and drop the media
     * notification. [resumeFromBackground] undoes it when the app comes back.
     */
    fun suspendInBackground() {}
    fun resumeFromBackground() {}

    /** Start or resume playback */
    fun play()

    /** Pause playback */
    fun pause()

    /** Seek to a specific position in milliseconds */
    fun seekTo(positionMs: Long)

    /** Select an audio track by index */
    fun setAudioTrack(index: Int)

    /** Select a subtitle track by index. Pass -1 to disable subtitles. */
    fun setSubtitleTrack(index: Int)

    /** Set the video aspect ratio */
    fun setAspectRatio(ratio: AspectRatio)

    /** Jump to the live edge of a live stream (resuming real-time broadcast) */
    fun jumpToLive()

    /** Stop playback. Can be resumed with play(). */
    fun stop()

    /**
     * Temporarily replaces the user's buffer setting (null = back to the setting). The buffer is
     * fixed when the underlying player is created, so a change drops the player; call it right
     * before [prepare], never in the middle of a playback you want to keep.
     */
    fun setBufferOverrideMs(ms: Int?)

    /** Release all resources. The player cannot be used after this. */
    fun release()

    /** Get the underlying platform player view for embedding in Compose */
    fun getPlayerView(): Any?
}
