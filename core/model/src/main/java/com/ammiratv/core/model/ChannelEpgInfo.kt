package com.ammiratv.core.model

import kotlinx.serialization.Serializable

/**
 * Encapsulates the current and next EPG program for a channel,
 * along with the calculated progress percentage for the on-air program.
 */
@Serializable
data class ChannelEpgInfo(
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null
) {
    val progressPercent: Float
        get() {
            val cur = currentProgram ?: return 0f
            val now = System.currentTimeMillis()
            val total = cur.endTime - cur.startTime
            if (total <= 0) return 0f
            return ((now - cur.startTime).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}
