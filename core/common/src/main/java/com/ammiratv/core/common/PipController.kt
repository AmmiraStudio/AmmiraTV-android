package com.ammiratv.core.common

/**
 * Process-wide flag telling the Activity whether Picture-in-Picture makes sense right now.
 * The player screen sets it while a stream is on screen; MainActivity reads it in onUserLeaveHint.
 */
object PipController {
    @Volatile
    var canEnterPip: Boolean = false
}
