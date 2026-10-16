package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.domain.repository.ChannelRepository
import com.ammiratv.domain.repository.EpgRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/** An EPG programme together with the channel it airs on (null when no channel maps to its EPG id). */
data class ProgramMatch(
    val program: EpgProgram,
    val channel: Channel?
)

data class UniversalSearchResult(
    val query: String = "",
    val channels: List<Channel> = emptyList(),
    val programs: List<ProgramMatch> = emptyList()
) {
    val isEmpty: Boolean get() = channels.isEmpty() && programs.isEmpty()
}

/**
 * One search box over live channels and upcoming/airing EPG programmes.
 * VOD and recordings join here when those features exist.
 */
class UniversalSearchUseCase @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository
) {
    suspend operator fun invoke(rawQuery: String): UniversalSearchResult {
        val query = rawQuery.trim()
        if (query.length < MIN_QUERY_LENGTH) return UniversalSearchResult(query)

        return coroutineScope {
            val channelsDeferred = async { channelRepository.searchChannels(query) }
            val programsDeferred = async { epgRepository.searchPrograms(query) }

            val programs = programsDeferred.await()
                .take(MAX_PROGRAMS)
                .map { program ->
                    // Best channel first: the DAO orders same-EPG-id duplicates by reliability
                    ProgramMatch(
                        program = program,
                        channel = channelRepository.getChannelsByTvgId(program.channelTvgId).firstOrNull()
                    )
                }

            UniversalSearchResult(
                query = query,
                channels = channelsDeferred.await(),
                programs = programs
            )
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_PROGRAMS = 30
    }
}
