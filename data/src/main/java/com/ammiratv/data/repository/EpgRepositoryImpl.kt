package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.EpgProgramDao
import com.ammiratv.core.database.entity.EpgProgramEntity
import com.ammiratv.core.database.entity.EpgSourceEntity
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.core.model.EpgSource
import com.ammiratv.domain.repository.EpgRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepositoryImpl @Inject constructor(
    private val epgProgramDao: EpgProgramDao
) : EpgRepository {

    private companion object {
        const val MAX_IDS_PER_QUERY = 400
    }

    override suspend fun getCurrentProgram(tvgId: String): EpgProgram? {
        val now = System.currentTimeMillis()
        return epgProgramDao.getCurrentProgram(tvgId, now)?.toDomain()
    }

    override suspend fun getNowAndNext(tvgIds: List<String>): Map<String, Pair<EpgProgram?, EpgProgram?>> {
        if (tvgIds.isEmpty()) return emptyMap()

        val now = System.currentTimeMillis()
        val maxTime = now + 12 * 3600 * 1000L // Next 12 hours
        // SQLite allows 999 bound variables on Android < 11: a long id list must be split
        val programs = tvgIds.distinct().chunked(MAX_IDS_PER_QUERY)
            .flatMap { epgProgramDao.getNowAndNext(it, now, maxTime) }

        val grouped = programs.groupBy { it.channelTvgId }
        val result = mutableMapOf<String, Pair<EpgProgram?, EpgProgram?>>()

        for ((tvgId, list) in grouped) {
            val current = list.firstOrNull { it.startTime <= now && it.endTime > now }
            val next = list.firstOrNull { it.startTime >= (current?.endTime ?: now) }
            result[tvgId] = Pair(current?.toDomain(), next?.toDomain())
        }

        return result
    }

    override fun getProgramsForChannel(tvgId: String, startTime: Long, endTime: Long): Flow<List<EpgProgram>> {
        return epgProgramDao.getProgramsForChannel(tvgId, startTime, endTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getProgramsForChannels(tvgIds: List<String>, startTime: Long, endTime: Long): Flow<Map<String, List<EpgProgram>>> {
        val chunks = tvgIds.distinct().chunked(MAX_IDS_PER_QUERY)
        if (chunks.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyMap())
        val flows = chunks.map { epgProgramDao.getProgramsForChannels(it, startTime, endTime) }
        val merged = if (flows.size == 1) flows[0]
        else kotlinx.coroutines.flow.combine(flows) { parts -> parts.toList().flatten() }
        return merged.map { entities ->
            entities.map { it.toDomain() }.groupBy { it.channelTvgId }
        }
    }

    override suspend fun searchPrograms(query: String): List<EpgProgram> {
        val now = System.currentTimeMillis()
        return epgProgramDao.searchPrograms(query, now).map { it.toDomain() }
    }

    override suspend fun savePrograms(programs: List<EpgProgram>) {
        val entities = programs.map { it.toEntity() }
        // Batch in chunks of 500
        entities.chunked(500).forEach { chunk ->
            epgProgramDao.insertAll(chunk)
        }
    }

    override suspend fun clearOldPrograms() {
        val cutoff = System.currentTimeMillis() - 24 * 3600 * 1000L // 24 hours ago
        epgProgramDao.deleteOlderThan(cutoff)
    }

    override suspend fun getKnownChannelIds(): List<String> = epgProgramDao.getDistinctChannelIds()

    override suspend fun getSources(): List<EpgSource> {
        return epgProgramDao.getSources().map {
            EpgSource(id = it.id, url = it.url, name = it.name, lastUpdated = it.lastUpdated)
        }
    }

    override suspend fun getOrCreateSource(url: String, name: String): Long {
        epgProgramDao.getSourceByUrl(url)?.let { return it.id }
        return epgProgramDao.insertSource(EpgSourceEntity(url = url, name = name))
    }

    override suspend fun watermark(sourceId: Long): Long = epgProgramDao.maxIdOfSource(sourceId)
    override suspend fun dropRowsUpTo(sourceId: Long, watermark: Long) = epgProgramDao.deleteBySourceUpTo(sourceId, watermark)
    override suspend fun dropRowsAfter(sourceId: Long, watermark: Long) = epgProgramDao.deleteBySourceAfter(sourceId, watermark)

    override suspend fun clearSource(sourceId: Long) {
        epgProgramDao.deleteBySource(sourceId)
    }

    override suspend fun markSourceUpdated(sourceId: Long) {
        epgProgramDao.touchSource(sourceId, System.currentTimeMillis())
    }
}

// ── Mappers ──────────────────────────────────────────────────────────────

private fun EpgProgramEntity.toDomain() = EpgProgram(
    id = id,
    epgSourceId = epgSourceId,
    channelTvgId = channelTvgId,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    category = category,
    posterUrl = posterUrl
)

private fun EpgProgram.toEntity() = EpgProgramEntity(
    id = id,
    epgSourceId = epgSourceId,
    channelTvgId = channelTvgId,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    category = category,
    posterUrl = posterUrl
)
