package com.frxe.music.island

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandInteractionPolicyTest {
    @Test
    fun compactTapExpandsAndExpandedTapCollapses() {
        assertTrue(IslandInteractionPolicy.nextExpandedState(false))
        assertFalse(IslandInteractionPolicy.nextExpandedState(true))
    }
}
