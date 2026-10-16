package com.ammiratv.domain.usecase

import com.ammiratv.core.model.EpgProgram
import com.ammiratv.domain.repository.EpgRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to fetch EPG program data across multiple channels
 * within a specific time window for the TV Guide Grid.
 */
class GetGridEpgUseCase @Inject constructor(
    private val epgRepository: EpgRepository
) {
    operator fun invoke(
        tvgIds: List<String>,
        startTime: Long,
        endTime: Long
    ): Flow<Map<String, List<EpgProgram>>> {
        return epgRepository.getProgramsForChannels(tvgIds, startTime, endTime)
    }
}
