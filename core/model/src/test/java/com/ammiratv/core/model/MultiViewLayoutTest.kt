package com.ammiratv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiViewLayoutTest {

    @Test
    fun roundTripKeepsEmptySlots() {
        val layout = MultiViewLayout("Serie A", listOf(10L, null, 30L, 40L))
        assertEquals(layout, MultiViewLayout.decode(layout.encode()))
    }

    @Test
    fun shortLayoutsArePaddedToFourSlots() {
        val decoded = MultiViewLayout.decode("Due|5,6")
        assertEquals(listOf(5L, 6L, null, null), decoded?.channelIds)
    }

    @Test
    fun nameWithSeparatorIsSanitised() {
        val layout = MultiViewLayout("A|B", listOf(1L, 2L, 3L, 4L))
        assertEquals("A/B", MultiViewLayout.decode(layout.encode())?.name)
    }

    @Test
    fun garbageIsRejected() {
        assertNull(MultiViewLayout.decode("nopipe"))
        assertNull(MultiViewLayout.decode("|1,2"))
    }

    @Test
    fun budgetFollowsTheProviderLimit() {
        assertEquals(4, MultiViewBudget.maxTiles(null))
        assertEquals(1, MultiViewBudget.maxTiles(1))
        assertEquals(2, MultiViewBudget.maxTiles(2))
        assertEquals(4, MultiViewBudget.maxTiles(10))
        assertEquals(1, MultiViewBudget.maxTiles(0))
    }
}
