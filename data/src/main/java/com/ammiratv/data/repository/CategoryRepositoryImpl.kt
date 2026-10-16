package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.CategoryDao
import com.ammiratv.core.database.entity.CategoryEntity
import com.ammiratv.core.model.Category
import com.ammiratv.core.model.StreamType
import com.ammiratv.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun getCategoriesByPlaylist(playlistId: Long): Flow<List<Category>> {
        return categoryDao.getByPlaylist(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getCategoriesByPlaylistAndType(playlistId: Long, type: StreamType): Flow<List<Category>> {
        return categoryDao.getByPlaylistAndType(playlistId, type.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveCategories(categories: List<Category>): List<Long> {
        return categoryDao.insertAll(categories.map { it.toEntity() })
    }

    override suspend fun deleteCategoriesByPlaylist(playlistId: Long) {
        categoryDao.deleteByPlaylist(playlistId)
    }
}

// ── Mappers ──────────────────────────────────────────────────────────────

private fun CategoryEntity.toDomain() = Category(
    id = id,
    playlistId = playlistId,
    name = name,
    type = try { StreamType.valueOf(type) } catch (_: Exception) { StreamType.LIVE },
    sortOrder = sortOrder,
    isHidden = isHidden
)

private fun Category.toEntity() = CategoryEntity(
    id = id,
    playlistId = playlistId,
    name = name,
    type = type.name,
    sortOrder = sortOrder,
    isHidden = isHidden
)
