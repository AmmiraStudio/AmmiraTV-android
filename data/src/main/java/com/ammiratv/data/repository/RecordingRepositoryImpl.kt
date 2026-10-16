package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.RecordingDao
import com.ammiratv.core.database.entity.RecordingEntity
import com.ammiratv.core.model.Recording
import com.ammiratv.core.model.RecordingStatus
import com.ammiratv.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepositoryImpl @Inject constructor(
    private val dao: RecordingDao
) : RecordingRepository {

    override fun observeAll(): Flow<List<Recording>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getById(id: Long) = dao.getById(id)?.toDomain()
    override suspend fun getScheduled() = dao.getScheduled().map { it.toDomain() }
    override suspend fun getRunning() = dao.getRunning().map { it.toDomain() }
    override suspend fun insert(recording: Recording) = dao.insert(recording.toEntity())
    override suspend fun update(recording: Recording) = dao.update(recording.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun RecordingEntity.toDomain() = Recording(
    id = id,
    channelName = channelName,
    title = title,
    streamUrl = streamUrl,
    startTime = startTime,
    endTime = endTime,
    status = runCatching { RecordingStatus.valueOf(status) }.getOrDefault(RecordingStatus.FAILED),
    filePath = filePath,
    sizeBytes = sizeBytes,
    errorMessage = errorMessage
)

private fun Recording.toEntity() = RecordingEntity(
    id = id,
    channelName = channelName,
    title = title,
    streamUrl = streamUrl,
    startTime = startTime,
    endTime = endTime,
    status = status.name,
    filePath = filePath,
    sizeBytes = sizeBytes,
    errorMessage = errorMessage
)
