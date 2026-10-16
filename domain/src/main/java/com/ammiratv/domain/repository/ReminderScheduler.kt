package com.ammiratv.domain.repository

/** Schedules a local notification shortly before a programme starts. */
interface ReminderScheduler {
    /**
     * @return false when the programme is too close or already started, so no reminder was set.
     */
    fun schedule(channelName: String, programTitle: String, startTimeMs: Long, requestKey: String): Boolean
}
