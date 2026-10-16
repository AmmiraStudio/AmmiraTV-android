package com.ammiratv.core.model

/**
 * Turns the raw player state into health signals for one stream: how fast it started, how long
 * it rebuffered, and — the case the player itself never reports — a frozen picture with no error.
 *
 * Pure on purpose (time is passed in) so the stall rules can be unit-tested.
 */
class StreamHealthMonitor(
    private val stallThresholdMs: Long = 15_000,
    /** Buffering shorter than this is normal seeking/chunk switching, not a rebuffer. */
    private val minRebufferMs: Long = 500
) {
    sealed interface Signal {
        data class StartOk(val timeToFirstFrameMs: Long) : Signal
        data object StartFailed : Signal
        data class Rebuffer(val durationMs: Long) : Signal

        /** Position stopped advancing although the player claims to be playing. */
        data object Stalled : Signal
    }

    private var streamStartedAt = 0L
    private var hasStarted = false
    private var failureReported = false
    private var bufferingSince: Long? = null
    private var lastPosition = -1L
    private var lastPositionChangeAt = 0L
    private var stallReported = false

    /** Call whenever a new stream is prepared (zap, retry, failover). */
    fun onNewStream(nowMs: Long) {
        streamStartedAt = nowMs
        hasStarted = false
        failureReported = false
        bufferingSince = null
        lastPosition = -1L
        lastPositionChangeAt = nowMs
        stallReported = false
    }

    fun onState(
        nowMs: Long,
        isPlaying: Boolean,
        isBuffering: Boolean,
        isError: Boolean,
        positionMs: Long
    ): List<Signal> {
        val signals = mutableListOf<Signal>()

        if (isError) {
            if (!hasStarted && !failureReported) {
                failureReported = true
                signals += Signal.StartFailed
            }
            return signals
        }

        if (!hasStarted) {
            if (isPlaying && !isBuffering) {
                hasStarted = true
                lastPosition = positionMs
                lastPositionChangeAt = nowMs
                signals += Signal.StartOk(nowMs - streamStartedAt)
            }
            return signals
        }

        // Rebuffering: measured from when buffering began to when it ended
        if (isBuffering) {
            if (bufferingSince == null) bufferingSince = nowMs
            // A buffering player is not "frozen": keep the stall clock fresh
            lastPositionChangeAt = nowMs
            return signals
        }
        bufferingSince?.let { since ->
            val duration = nowMs - since
            if (duration >= minRebufferMs) signals += Signal.Rebuffer(duration)
            bufferingSince = null
        }

        if (isPlaying) {
            if (positionMs != lastPosition) {
                lastPosition = positionMs
                lastPositionChangeAt = nowMs
                stallReported = false
            } else if (!stallReported && nowMs - lastPositionChangeAt >= stallThresholdMs) {
                stallReported = true
                signals += Signal.Stalled
            }
        } else {
            // Paused by the user: the clock must not run
            lastPositionChangeAt = nowMs
        }
        return signals
    }
}
