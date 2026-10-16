package com.ammiratv.domain.usecase

import com.ammiratv.core.model.EpgProgram
import com.ammiratv.domain.repository.EpgRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChannelEpgUseCase @Inject constructor(
    private val epgRepository: EpgRepository
) {
    operator fun invoke(tvgId: String, startTime: Long, endTime: Long): Flow<List<EpgProgram>> {
        return epgRepository.getProgramsForChannel(tvgId, startTime, endTime)
    }
}
