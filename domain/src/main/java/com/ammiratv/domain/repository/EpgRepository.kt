package com.ammiratv.domain.repository

import com.ammiratv.core.model.EpgProgram
import com.ammiratv.core.model.EpgSource
import kotlinx.coroutines.flow.Flow

interface EpgRepository {
    /** Id watermark of a source's current rows; see [dropRowsUpTo] / [dropRowsAfter]. */
    suspend fun watermark(sourceId: Long): Long
    /** After a successful import: removes the previous guide (rows at or below [watermark]). */
    suspend fun dropRowsUpTo(sourceId: Long, watermark: Long)
    /** After a failed import: removes the partial new rows, keeping the previous guide intact. */
    suspend fun dropRowsAfter(sourceId: Long, watermark: Long)

    suspend fun getCurrentProgram(tvgId: String): EpgProgram?
    suspend fun getNowAndNext(tvgIds: List<String>): Map<String, Pair<EpgProgram?, EpgProgram?>>
    fun getProgramsForChannel(tvgId: String, startTime: Long, endTime: Long): Flow<List<EpgProgram>>
    fun getProgramsForChannels(tvgIds: List<String>, startTime: Long, endTime: Long): Flow<Map<String, List<EpgProgram>>>
    suspend fun searchPrograms(query: String): List<EpgProgram>
    suspend fun savePrograms(programs: List<EpgProgram>)
    suspend fun clearOldPrograms()

    /** Channel ids that have at least one programme stored. */
    suspend fun getKnownChannelIds(): List<String>

    // ── Sources ──────────────────────────────────────────────────────────
    suspend fun getSources(): List<EpgSource>

    /** Finds the source with this URL or creates it. Programs need an existing source row (FK). */
    suspend fun getOrCreateSource(url: String, name: String): Long

    /** Removes every program that came from [sourceId], so a re-import never duplicates entries. */
    suspend fun clearSource(sourceId: Long)
    suspend fun markSourceUpdated(sourceId: Long)
}
