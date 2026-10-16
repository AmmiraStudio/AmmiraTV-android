package com.ammiratv.domain.usecase

import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.EpgProgram
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.network.catchup.CatchupUrlGenerator
import com.ammiratv.domain.repository.PlaylistRepository
import javax.inject.Inject

/** What pressing OK on a guide entry should do, decided by where the programme sits in time. */
enum class ProgramAction { WATCH_CATCHUP, WATCH_LIVE, RECORD }

object ProgramActions {
    fun forProgram(program: EpgProgram, nowMs: Long): ProgramAction = when {
        program.endTime <= nowMs -> ProgramAction.WATCH_CATCHUP
        program.startTime <= nowMs -> ProgramAction.WATCH_LIVE
        else -> ProgramAction.RECORD
    }
}

/**
 * Builds the archive URL of a past programme. Only Xtream accounts expose a timeshift endpoint
 * here; whether a channel really keeps an archive is up to the provider (the request just fails
 * otherwise, and the player reports it).
 */
class BuildCatchupUrlUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(channel: Channel, program: EpgProgram): String? {
        val playlist = playlistRepository.getPlaylistById(channel.playlistId) ?: return null
        if (playlist.type != PlaylistType.XTREAM) return null
        val user = playlist.username ?: return null
        val pass = playlist.password ?: return null

        // Xtream live URLs end with ".../live/<user>/<pass>/<streamId>.<ext>"
        val streamId = STREAM_ID.find(channel.streamUrl)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        return CatchupUrlGenerator.buildXtreamTimeshiftUrl(
            serverUrl = playlist.url,
            username = user,
            password = pass,
            streamId = streamId,
            startTimeEpochMs = program.startTime,
            endTimeEpochMs = program.endTime
        )
    }

    private companion object {
        val STREAM_ID = Regex("/(\\d+)\\.[A-Za-z0-9]+(?:\\?.*)?$")
    }
}
