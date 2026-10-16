package com.ammiratv.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionBudgetTest {

    @Test
    fun unknownLimitAlwaysAllows() {
        assertTrue(ConnectionBudget.canOpenAnother(null, 5, 5))
        assertTrue(ConnectionBudget.canOpenAnother(0, 5, 5))
    }

    @Test
    fun singleConnectionAccountBlocksSecondStream() {
        assertFalse(ConnectionBudget.canOpenAnother(maxConnections = 1, providerActive = 1, inUse = 1))
    }

    @Test
    fun twoConnectionsAllowOneExtraWhileWatching() {
        assertTrue(ConnectionBudget.canOpenAnother(maxConnections = 2, providerActive = 1, inUse = 1))
        assertFalse(ConnectionBudget.canOpenAnother(maxConnections = 2, providerActive = 2, inUse = 1))
    }

    @Test
    fun otherDevicesOnTheAccountAreCounted() {
        // We have nothing open, but another device already uses the only slot
        assertFalse(ConnectionBudget.canOpenAnother(maxConnections = 1, providerActive = 1, inUse = 0))
    }
}
