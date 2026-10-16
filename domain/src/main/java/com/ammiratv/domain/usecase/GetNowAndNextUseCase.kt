package com.ammiratv.domain.usecase

import com.ammiratv.core.model.ChannelEpgInfo
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.domain.repository.EpgRepository
import javax.inject.Inject

class GetNowAndNextUseCase @Inject constructor(
    private val epgRepository: EpgRepository
) {
    suspend operator fun invoke(tvgIds: List<String>): Map<String, ChannelEpgInfo> {
        val raw = epgRepository.getNowAndNext(tvgIds)
        return raw.mapValues { (_, pair) ->
            ChannelEpgInfo(
                currentProgram = pair.first,
                nextProgram = pair.second
            )
        }
    }

    suspend fun getForChannel(tvgId: String): ChannelEpgInfo {
        val map = invoke(listOf(tvgId))
        return map[tvgId] ?: ChannelEpgInfo()
    }
}
