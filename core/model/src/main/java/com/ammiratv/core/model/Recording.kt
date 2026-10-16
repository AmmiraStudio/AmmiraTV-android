package com.ammiratv.core.model

enum class RecordingStatus { SCHEDULED, RECORDING, DONE, FAILED, CANCELLED }

data class Recording(
    val id: Long = 0,
    val channelName: String,
    val title: String,
    val streamUrl: String,
    val startTime: Long,
    val endTime: Long,
    val status: RecordingStatus,
    val filePath: String? = null,
    val sizeBytes: Long = 0,
    val errorMessage: String? = null
) {
    val isActive: Boolean get() = status == RecordingStatus.SCHEDULED || status == RecordingStatus.RECORDING
    val durationMs: Long get() = (endTime - startTime).coerceAtLeast(0)
}

/**
 * Decides whether one more simultaneous stream fits in the provider's connection limit.
 * Most Xtream accounts allow only 1–2: a recording while watching live uses a second one.
 */
object ConnectionBudget {
    /**
     * @param inUse streams this app has open right now (the live player counts as one)
     * @param providerActive connections the provider reports as active (may include other devices)
     * @return true if another stream can be opened; true as well when the limit is unknown.
     */
    fun canOpenAnother(maxConnections: Int?, providerActive: Int, inUse: Int): Boolean {
        if (maxConnections == null || maxConnections <= 0) return true
        // The provider's counter already includes our own open streams, so take the larger view.
        return maxOf(providerActive, inUse) + 1 <= maxConnections
    }
}
