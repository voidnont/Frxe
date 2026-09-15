package com.frxe.music.playback

import com.frxe.music.source.PlaybackResolverKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPrefetchPolicyTest {
    @Test
    fun prefetchKeepsYouTubeMusicFirst() {
        assertEquals(
            listOf(
                PlaybackResolverKind.InnerTube,
                PlaybackResolverKind.NewPipe,
                PlaybackResolverKind.YtDlp
            ),
            PlaybackPrefetchPolicy.resolverOrder
        )
    }
}
