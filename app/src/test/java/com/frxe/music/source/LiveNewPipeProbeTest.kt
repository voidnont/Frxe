package com.frxe.music.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LiveNewPipeProbeTest {
    @Test
    fun newPipeResolvesStablePublicVideo() = runBlocking {
        val result = YouTubeAudioResolverRuntime.resolve(
            videoId = "jNQXAC9IVRw",
            order = listOf(PlaybackResolverKind.NewPipe)
        )

        when (result) {
            is PlaybackResolutionResult.Success -> {
                assertEquals(
                    PlaybackResolverKind.NewPipe,
                    result.stream.resolver
                )
                assertTrue(
                    result.stream.url.startsWith("https://") ||
                        result.stream.url.startsWith("http://")
                )
            }

            is PlaybackResolutionResult.VerificationRequired ->
                fail("NewPipe hit verification: ${result.challenge.message}")

            is PlaybackResolutionResult.Failed ->
                fail("NewPipe failed: ${result.message}")
        }
    }
}
