package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.ManualChannelNumberDao
import com.ammiratv.core.database.entity.ManualChannelNumberEntity
import com.ammiratv.domain.repository.ManualNumberRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualNumberRepositoryImpl @Inject constructor(
    private val dao: ManualChannelNumberDao
) : ManualNumberRepository {
    override suspend fun all(): Map<String, Int> = dao.getAll().associate { it.key to it.number }
    override suspend fun set(key: String, number: Int) = dao.upsert(ManualChannelNumberEntity(key, number))
    override suspend fun remove(key: String) = dao.delete(key)
}
