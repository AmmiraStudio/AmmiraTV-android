package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Category
import com.ammiratv.core.model.StreamType
import com.ammiratv.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(playlistId: Long, type: StreamType = StreamType.LIVE): Flow<List<Category>> {
        return categoryRepository.getCategoriesByPlaylistAndType(playlistId, type)
    }
}
