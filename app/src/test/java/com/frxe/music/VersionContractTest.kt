package com.frxe.music

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionContractTest {
    @Test
    fun androidReleaseIsZeroSixEight() {
        assertEquals("0.6.8", BuildConfig.VERSION_NAME)
        assertEquals(26, BuildConfig.VERSION_CODE)
    }
}
