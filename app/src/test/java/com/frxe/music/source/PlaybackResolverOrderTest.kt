package com.frxe.music.source

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResolverOrderTest {
    @Test
    fun fastResolversRunBeforeYtDlpFallback() {
        assertEquals(
            listOf(
                PlaybackResolverKind.InnerTube,
                PlaybackResolverKind.NewPipe,
                PlaybackResolverKind.YtDlp
            ),
            PlaybackResolverOrder.local
        )
    }
}
