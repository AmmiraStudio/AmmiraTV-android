package com.ammiratv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ammiratv.core.database.entity.EpgProgramEntity
import com.ammiratv.core.database.entity.EpgSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgProgramDao {

    /** Get current program for a channel (what's on now) */
    @Query("SELECT * FROM epg_program WHERE channelTvgId = :tvgId AND startTime <= :now AND endTime > :now LIMIT 1")
    suspend fun getCurrentProgram(tvgId: String, now: Long): EpgProgramEntity?

    /** Get programs for a channel in a time range (for EPG grid) */
    @Query("SELECT * FROM epg_program WHERE channelTvgId = :tvgId AND endTime > :startTime AND startTime < :endTime ORDER BY startTime ASC")
    fun getProgramsForChannel(tvgId: String, startTime: Long, endTime: Long): Flow<List<EpgProgramEntity>>

    /** Get programs for multiple channels in a time range (for EPG grid) */
    @Query("SELECT * FROM epg_program WHERE channelTvgId IN (:tvgIds) AND endTime > :startTime AND startTime < :endTime ORDER BY channelTvgId, startTime ASC")
    fun getProgramsForChannels(tvgIds: List<String>, startTime: Long, endTime: Long): Flow<List<EpgProgramEntity>>

    /** Get now and next programs for multiple channels */
    @Query("SELECT * FROM epg_program WHERE channelTvgId IN (:tvgIds) AND endTime > :now AND startTime < :maxTime ORDER BY channelTvgId, startTime ASC")
    suspend fun getNowAndNext(tvgIds: List<String>, now: Long, maxTime: Long): List<EpgProgramEntity>

    /** Search programs by title */
    @Query("SELECT * FROM epg_program WHERE title LIKE '%' || :query || '%' AND endTime > :now ORDER BY startTime ASC LIMIT :limit")
    suspend fun searchPrograms(query: String, now: Long, limit: Int = 50): List<EpgProgramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_program WHERE epgSourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    /** Highest row id of a source (0 when empty): marks "everything older than this import". */
    @Query("SELECT COALESCE(MAX(id), 0) FROM epg_program WHERE epgSourceId = :sourceId")
    suspend fun maxIdOfSource(sourceId: Long): Long

    @Query("DELETE FROM epg_program WHERE epgSourceId = :sourceId AND id <= :maxId")
    suspend fun deleteBySourceUpTo(sourceId: Long, maxId: Long)

    @Query("DELETE FROM epg_program WHERE epgSourceId = :sourceId AND id > :minId")
    suspend fun deleteBySourceAfter(sourceId: Long, minId: Long)

    // ── EPG sources ──────────────────────────────────────────────────────
    // Programs have a FK on epg_source, so a source row must exist before any insert.

    /** Channel ids that really have programmes: the candidates for automatic matching. */
    @Query("SELECT DISTINCT channelTvgId FROM epg_program")
    suspend fun getDistinctChannelIds(): List<String>

    @Query("SELECT * FROM epg_source")
    suspend fun getSources(): List<EpgSourceEntity>

    @Query("SELECT * FROM epg_source WHERE url = :url LIMIT 1")
    suspend fun getSourceByUrl(url: String): EpgSourceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSource(source: EpgSourceEntity): Long

    @Query("UPDATE epg_source SET lastUpdated = :timestamp WHERE id = :sourceId")
    suspend fun touchSource(sourceId: Long, timestamp: Long)

    /** Clean up old programs to save space */
    @Query("DELETE FROM epg_program WHERE endTime < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)
}
