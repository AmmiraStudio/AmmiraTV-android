package com.ammiratv.domain.repository

import com.ammiratv.core.model.Recording
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RecordingRepository {
    fun observeAll(): Flow<List<Recording>>
    suspend fun getById(id: Long): Recording?
    suspend fun getScheduled(): List<Recording>
    suspend fun getRunning(): List<Recording>
    suspend fun insert(recording: Recording): Long
    suspend fun update(recording: Recording)
    suspend fun delete(id: Long)
}

/**
 * Executes recordings. Implemented in :data because it needs files, OkHttp and a foreground
 * service; the domain only schedules and stops.
 */
interface RecordingEngine {
    /** Number of recordings writing to disk right now. */
    val runningCount: StateFlow<Int>

    /** Arms every SCHEDULED recording again (timers do not survive process death). Call at start-up. */
    suspend fun rearmScheduled()

    /** Persists a SCHEDULED recording and arms its timer. Returns its id. */
    suspend fun schedule(recording: Recording): Long

    /** Stops a running recording (keeps the file) or cancels a scheduled one. */
    suspend fun stop(id: Long)

    suspend fun delete(id: Long)
}
