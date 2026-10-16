package com.ammiratv.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.ammiratv.core.datastore.AmmiraTvPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.ammiratv.core.model.AspectRatio
import com.ammiratv.core.model.PlayerState
import com.ammiratv.core.model.TrackInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media3/ExoPlayer implementation of PlayerEngine.
 * 
 * Handles HLS, DASH, progressive and MPEG-TS streams.
 * Reports state changes via a StateFlow that the UI observes.
 *
 * Buffer strategy: minimal buffer for fast zapping (2s min, 5s max),
 * with preloading of adjacent channels planned for Phase 1.
 */
@Singleton
class Media3PlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AmmiraTvPreferences
) : PlayerEngine {

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var positionUpdateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // Settings are mirrored here so that creating a player never blocks the main thread on disk.
    @Volatile private var cachedBufferMs: Int? = null
    @Volatile private var cachedVolumeBoost: Boolean? = null

    init {
        scope.launch { preferences.bufferSizeMs.collect { cachedBufferMs = it } }
        scope.launch { preferences.volumeBoost.collect { cachedVolumeBoost = it } }
    }

    /** Media session shared with [PlaybackService]; non-null while a player exists. */
    @Volatile
    var mediaSession: androidx.media3.session.MediaSession? = null
        private set
    private var sessionController: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>? = null

    private var nowPlayingTitle: String? = null
    private var nowPlayingSubtitle: String? = null

    override fun setNowPlaying(title: String?, subtitle: String?) {
        nowPlayingTitle = title
        nowPlayingSubtitle = subtitle
    }

    // ── Reconnection ─────────────────────────────────────────────────────
    private var trackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null

    /** Changes the track selection without losing the "do not exceed capabilities" setting. */
    private inline fun updateTracks(change: androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters.Builder.() -> Unit) {
        val ts = trackSelector ?: return
        ts.setParameters(ts.buildUponParameters().apply(change))
    }

    private var decoderLabel: String? = null
    private var firstFrameWatchdog: Job? = null
    private var loweredQuality = false

    /**
     * Sound but no picture after a few seconds usually means the device cannot cope with the
     * stream's resolution/bitrate. Once per channel, cap the quality and restart it.
     */
    private fun armFirstFrameWatchdog() {
        firstFrameWatchdog?.cancel()
        firstFrameWatchdog = scope.launch {
            // No picture yet: step the quality down twice (720p, then 480p), re-trying each time
            for ((width, height, bitrate) in listOf(Triple(1280, 720, 4_000_000), Triple(854, 480, 1_500_000))) {
                delay(8_000)
                val player = exoPlayer ?: return@launch
                if (_state.value.firstFrameRendered || _state.value.isError) return@launch
                updateTracks { setMaxVideoSize(width, height); setMaxVideoBitrate(bitrate) }
                loweredQuality = true
                player.prepare()
            }
        }
    }

    private var lastUri: String? = null
    private var retryAttempt = 0
    private var retryJob: Job? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return true
        return runCatching { cm.activeNetworkInfo?.isConnected == true }.getOrDefault(true)
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                // Wi-Fi <-> mobile switch or connection back: retry at once instead of waiting
                scope.launch {
                    val waiting = retryJob?.isActive == true || _state.value.isError || _state.value.isReconnecting
                    if (waiting && lastUri != null) {
                        retryAttempt = 0
                        retryNow()
                    }
                }
            }
        }
        runCatching {
            cm.registerNetworkCallback(
                android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
            networkCallback = callback
        }
    }

    private fun unregisterNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        networkCallback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun retryNow() {
        retryJob?.cancel()
        val player = exoPlayer ?: return
        _state.update { it.copy(isReconnecting = true, isBuffering = true, isError = false, errorMessage = null) }
        player.prepare()
        player.playWhenReady = true
    }

    private fun scheduleRetry() {
        retryJob?.cancel()
        retryAttempt++
        val delayMs = (1_000L shl (retryAttempt - 1)).coerceAtMost(8_000L)
        _state.update { it.copy(isReconnecting = true, isBuffering = true, isError = false, errorMessage = null) }
        retryJob = scope.launch {
            delay(delayMs)
            // No network at all: do not burn attempts, the callback restarts us when it is back
            if (!isOnline()) return@launch
            exoPlayer?.let { it.prepare(); it.playWhenReady = true }
        }
    }

    private fun isRecoverable(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT -> true
        else -> false
    }

    @OptIn(UnstableApi::class)
    private fun getOrCreatePlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        // The buffer profile is a user setting (or the learned override); read once, only when
        // the player is (re)created.
        val minBufferMs = (bufferOverrideMs ?: cachedBufferMs ?: runBlocking { preferences.bufferSizeMs.first() })
            .coerceIn(500, 30_000)

        // IPTV servers often redirect http -> https (or back): ExoPlayer refuses that by default.
        // A neutral User-Agent and generous timeouts help on slow Wi-Fi chips as well.
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("AmmiraTv/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        // If the preferred (hardware) decoder cannot start, try the next one instead of leaving a
        // black picture with sound. Weak boxes hit this with some H.265 / high-profile streams.
        val renderers = androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        // Never ask for more resolution than the screen has. (A variant the device only "maybe"
        // supports is still tried: refusing it would leave sound without picture on boxes that
        // report their decoder limits conservatively; the first-frame watchdog steps quality down.)
        val selector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).also { ts ->
            ts.setParameters(
                ts.buildUponParameters()
                    .setViewportSizeToPhysicalDisplaySize(context, true)
            )
            trackSelector = ts
        }

        return ExoPlayer.Builder(context, renderers)
            .setTrackSelector(selector)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(httpFactory)
            )
            // Pause for calls and other apps, resume afterwards; stop when headphones are unplugged
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(
                // Small buffer = fast zapping; the user can trade speed for stability.
                // Media3 requires: playback thresholds <= minBuffer <= maxBuffer.
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ minBufferMs,
                        /* maxBufferMs = */ maxOf(15_000, minBufferMs * 3),
                        /* bufferForPlaybackMs = */ (minBufferMs / 4).coerceIn(250, minBufferMs),
                        /* bufferForPlaybackAfterRebufferMs = */ (minBufferMs * 3 / 4).coerceIn(500, minBufferMs)
                    )
                    .build()
            )
            .build()
            .also { player ->
                exoPlayer = player
                player.addListener(createPlayerListener())
                player.addAnalyticsListener(createAnalyticsListener())
                startPositionTracking()
                registerNetworkCallback()
                createSession(player)
            }
    }

    @OptIn(UnstableApi::class)
    private fun createSession(player: ExoPlayer) {
        runCatching {
            mediaSession?.release()
            mediaSession = androidx.media3.session.MediaSession.Builder(context, player).build()
            // Binding a controller starts PlaybackService, which owns the media notification
            sessionController?.let { androidx.media3.session.MediaController.releaseFuture(it) }
            sessionController = androidx.media3.session.MediaController.Builder(
                context,
                androidx.media3.session.SessionToken(
                    context,
                    android.content.ComponentName(context, PlaybackService::class.java)
                )
            ).buildAsync()
        }
    }

    override fun prepare(uri: String, headers: Map<String, String>) {
        val player = getOrCreatePlayer()
        lastUri = uri
        retryAttempt = 0
        retryJob?.cancel()
        loweredQuality = false
        decoderLabel = null
        updateTracks { clearVideoSizeConstraints(); setMaxVideoBitrate(Int.MAX_VALUE) }
        _state.update { it.copy(firstFrameRendered = false, videoInfo = null) }
        armFirstFrameWatchdog()

        val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(nowPlayingTitle)
                    .setArtist(nowPlayingSubtitle)
                    .build()
            )

        // Add headers if provided (e.g., for auth tokens)
        if (headers.isNotEmpty()) {
            mediaItemBuilder.setUri(
                androidx.media3.common.MediaItem.fromUri(uri).buildUpon()
                    .setUri(uri)
                    .build()
                    .localConfiguration?.uri ?: android.net.Uri.parse(uri)
            )
        }

        val mediaItem = mediaItemBuilder.build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        _state.update { it.copy(isBuffering = true, isError = false, isReconnecting = false, errorMessage = null) }
    }

    private var suspendedPlaying = false

    override fun suspendInBackground() {
        val player = exoPlayer ?: return
        suspendedPlaying = player.playWhenReady
        player.pause()
        // Releasing the controller lets the playback service stop, which removes its notification
        sessionController?.let { androidx.media3.session.MediaController.releaseFuture(it) }
        sessionController = null
        runCatching { context.stopService(android.content.Intent(context, PlaybackService::class.java)) }
    }

    @OptIn(UnstableApi::class)
    override fun resumeFromBackground() {
        val player = exoPlayer ?: return
        if (!suspendedPlaying) return
        suspendedPlaying = false
        // A live stream moved on while paused: rejoin the live edge instead of the old position
        if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
        mediaSession?.let { createController() }
        player.play()
    }

    @OptIn(UnstableApi::class)
    private fun createController() {
        runCatching {
            sessionController = androidx.media3.session.MediaController.Builder(
                context,
                androidx.media3.session.SessionToken(
                    context,
                    android.content.ComponentName(context, PlaybackService::class.java)
                )
            ).buildAsync()
        }
    }

    override fun play() {
        exoPlayer?.play()
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    @OptIn(UnstableApi::class)
    override fun setAudioTrack(index: Int) {
        val player = exoPlayer ?: return
        val trackGroups = player.currentTracks.groups
        for (group in trackGroups) {
            if (group.type == C.TRACK_TYPE_AUDIO && group.length > index) {
                updateTracks { setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, index)
                    ) }
                break
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun setSubtitleTrack(index: Int) {
        val player = exoPlayer ?: return
        if (index == -1) {
            // Disable subtitles
            updateTracks { setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) }
            return
        }
        val trackGroups = player.currentTracks.groups
        for (group in trackGroups) {
            if (group.type == C.TRACK_TYPE_TEXT && group.length > index) {
                updateTracks { setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false); setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, index)
                    ) }
                break
            }
        }
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        _state.update { it.copy(aspectRatio = ratio) }
        // Aspect ratio is applied by the UI layer on the PlayerView/Surface
    }

    override fun jumpToLive() {
        exoPlayer?.seekToDefaultPosition()
    }

    override fun stop() {
        exoPlayer?.stop()
    }

    @Volatile
    private var bufferOverrideMs: Int? = null

    override fun setBufferOverrideMs(ms: Int?) {
        if (bufferOverrideMs == ms) return
        bufferOverrideMs = ms
        // Next prepare() builds a player with the new load control
        if (exoPlayer != null) release()
    }

    // Data usage: bytes the player downloaded, summed per day. Written in ~1 MB steps so a
    // 4 Mbps stream does not hit the disk on every bandwidth sample.
    private var pendingUsageBytes = 0L

    private fun recordDataUsage(bytes: Long) {
        if (bytes <= 0) return
        pendingUsageBytes += bytes
        if (pendingUsageBytes < 1_000_000L) return
        val toWrite = pendingUsageBytes
        pendingUsageBytes = 0
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        scope.launch { preferences.addDataUsage(day, toWrite) }
    }

    // Night/dialogue mode: +6 dB through the platform LoudnessEnhancer, attached to the player's
    // audio session. Re-attached on every READY because the session id changes with the player.
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    private fun applyVolumeBoost() {
        val player = exoPlayer ?: return
        val boost = cachedVolumeBoost ?: runBlocking { preferences.volumeBoost.first() }
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            if (boost && player.audioSessionId != 0) {
                loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(player.audioSessionId).apply {
                    setTargetGain(600) // millibels
                    enabled = true
                }
            }
        } catch (_: RuntimeException) {
            // Some boxes ship without the effect; playback must not depend on it
            loudnessEnhancer = null
        }
    }

    override fun release() {
        retryJob?.cancel()
        unregisterNetworkCallback()
        sessionController?.let { androidx.media3.session.MediaController.releaseFuture(it) }
        sessionController = null
        mediaSession?.release()
        mediaSession = null
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        positionUpdateJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        _state.update { PlayerState() }
    }

    override fun getPlayerView(): Any? = exoPlayer

    // ── Private helpers ──────────────────────────────────────────────────

    private fun createPlayerListener() = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            if (videoGroups.isEmpty()) return
            val anySupported = videoGroups.any { g -> (0 until g.length).any { g.isTrackSupported(it, true) } }
            if (!anySupported) {
                val mime = videoGroups.first().getTrackFormat(0).sampleMimeType?.substringAfter("video/")?.uppercase()
                // Sound with no picture is worse than an error: report it so the app can try the
                // channel's other variants (SD / H.264) instead of showing a black screen.
                _state.update {
                    it.copy(
                        isError = true,
                        isBuffering = false,
                        errorMessage = "Questo dispositivo non supporta il formato video del canale" +
                            (mime?.let { m -> " ($m)" } ?: "")
                    )
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING
                )
            }
            if (playbackState == Player.STATE_READY) {
                retryAttempt = 0
                _state.update { it.copy(isReconnecting = false) }
                updateTrackInfo()
                applyVolumeBoost()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Live window slid past us (long pause / stall): just rejoin the live edge
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                exoPlayer?.let { it.seekToDefaultPosition(); it.prepare() }
                return
            }
            if (isRecoverable(error) && retryAttempt < MAX_RETRIES && lastUri != null) {
                scheduleRetry()
                return
            }
            retryJob?.cancel()
            _state.update {
                it.copy(
                    isError = true,
                    isReconnecting = false,
                    isPlaying = false,
                    errorMessage = error.message ?: "Playback error"
                )
            }
        }
    }

    /** Network/decoder numbers for the diagnostics screen and the "your line or the provider?" verdict. */
    @OptIn(UnstableApi::class)
    private fun createAnalyticsListener() = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
        override fun onBandwidthEstimate(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long
        ) {
            _state.update { it.copy(bandwidthBps = bitrateEstimate) }
            recordDataUsage(totalBytesLoaded)
        }

        override fun onVideoDecoderInitialized(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            decoderLabel = decoderName
        }

        override fun onVideoSizeChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            videoSize: androidx.media3.common.VideoSize
        ) {
            val fmt = exoPlayer?.videoFormat?.sampleMimeType?.substringAfter("video/")?.uppercase().orEmpty()
            _state.update {
                it.copy(videoInfo = "$fmt ${videoSize.width}x${videoSize.height}  ${decoderLabel.orEmpty()}".trim())
            }
        }

        override fun onRenderedFirstFrame(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long
        ) {
            firstFrameWatchdog?.cancel()
            _state.update { it.copy(firstFrameRendered = true) }
        }

        override fun onDroppedVideoFrames(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            _state.update { it.copy(droppedFrames = it.droppedFrames + droppedFrames) }
        }

        override fun onVideoInputFormatChanged(
            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
            format: androidx.media3.common.Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
        ) {
            _state.update {
                it.copy(videoBitrateBps = format.bitrate.toLong().coerceAtLeast(0L))
            }
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }

    /** Periodically update playback position for the UI progress bar */
    private fun startPositionTracking() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    // Live diagnostics for the info line: what is being decoded right now
                    val vf = player.videoFormat
                    val af = player.audioFormat
                    val stateName = when (player.playbackState) {
                        Player.STATE_IDLE -> "idle"; Player.STATE_BUFFERING -> "buffering"
                        Player.STATE_READY -> "ready"; else -> "ended"
                    }
                    val diag = "V " + (vf?.let { "${it.width}x${it.height} ${it.codecs ?: it.sampleMimeType}" } ?: "nessun video") +
                        "  A " + (af?.let { it.codecs ?: it.sampleMimeType } ?: "nessun audio") +
                        "  " + stateName + (decoderLabel?.let { "  $it" } ?: "")
                    _state.update {
                        it.copy(
                            videoInfo = diag,
                            currentPositionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(0),
                            bufferedPositionMs = player.bufferedPosition
                        )
                    }
                }
                delay(500) // Update every 500ms
            }
        }
    }

    /** Extract available audio and subtitle tracks from ExoPlayer */
    private fun updateTrackInfo() {
        val player = exoPlayer ?: return
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()

        for (group in player.currentTracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        audioTracks.add(
                            TrackInfo(
                                index = i,
                                name = format.label ?: "Audio ${i + 1}",
                                language = format.language,
                                isSelected = group.isTrackSelected(i)
                            )
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        subtitleTracks.add(
                            TrackInfo(
                                index = i,
                                name = format.label ?: "Subtitle ${i + 1}",
                                language = format.language,
                                isSelected = group.isTrackSelected(i)
                            )
                        )
                    }
                }
            }
        }

        _state.update {
            it.copy(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks
            )
        }
    }
}
