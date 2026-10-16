package com.ammiratv.domain.usecase

import com.ammiratv.core.model.EpgProgram
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramActionsTest {

    private val now = 1_000_000L

    private fun program(start: Long, end: Long) =
        EpgProgram(channelTvgId = "x", title = "T", startTime = start, endTime = end)

    @Test
    fun pastProgrammeOpensCatchup() {
        assertEquals(ProgramAction.WATCH_CATCHUP, ProgramActions.forProgram(program(100, now - 1), now))
        assertEquals(ProgramAction.WATCH_CATCHUP, ProgramActions.forProgram(program(100, now), now))
    }

    @Test
    fun airingProgrammePlaysLive() {
        assertEquals(ProgramAction.WATCH_LIVE, ProgramActions.forProgram(program(now - 10, now + 10), now))
        assertEquals(ProgramAction.WATCH_LIVE, ProgramActions.forProgram(program(now, now + 10), now))
    }

    @Test
    fun futureProgrammeIsRecorded() {
        assertEquals(ProgramAction.RECORD, ProgramActions.forProgram(program(now + 1, now + 100), now))
    }
}
