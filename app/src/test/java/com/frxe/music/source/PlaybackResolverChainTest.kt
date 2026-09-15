package com.frxe.music.source

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResolverChainTest {
    @Test
    fun startsFallbackResolversWhilePrimaryIsStillRunning() = runBlocking {
        val primaryRelease = CompletableDeferred<Unit>()
        val innerTubeStarted = CompletableDeferred<Unit>()
        val newPipeStarted = CompletableDeferred<Unit>()

        val resolution = async {
            PlaybackResolverChain(
                listOf(
                    PlaybackResolverKind.YtDlp to suspend {
                        primaryRelease.await()
                        null
                    },
                    PlaybackResolverKind.InnerTube to suspend {
                        innerTubeStarted.complete(Unit)
                        ResolvedAudioCandidate("https://example.com/audio.m4a")
                    },
                    PlaybackResolverKind.NewPipe to suspend {
                        newPipeStarted.complete(Unit)
                        null
                    }
                )
            ).resolve()
        }

        withTimeout(2_000L) {
            innerTubeStarted.await()
            newPipeStarted.await()
        }

        val result = withTimeout(1_000L) {
            resolution.await()
        }

        assertTrue(result is PlaybackResolutionResult.Success)
        assertEquals(
            PlaybackResolverKind.InnerTube,
            (result as PlaybackResolutionResult.Success).stream.resolver
        )
        assertTrue(!primaryRelease.isCompleted)
    }
}
