package com.ammiratv.domain.repository

import com.ammiratv.core.model.Category
import com.ammiratv.core.model.StreamType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getCategoriesByPlaylist(playlistId: Long): Flow<List<Category>>
    fun getCategoriesByPlaylistAndType(playlistId: Long, type: StreamType): Flow<List<Category>>

    /** Persists [categories] and returns the generated ids in the same order. */
    suspend fun saveCategories(categories: List<Category>): List<Long>
    suspend fun deleteCategoriesByPlaylist(playlistId: Long)
}
