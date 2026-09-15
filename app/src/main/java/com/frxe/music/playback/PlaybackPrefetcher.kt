package com.frxe.music.playback

import com.frxe.music.source.PlaybackResolutionResult
import com.frxe.music.source.PlaybackStreamResolver
import com.frxe.music.source.YouTubeAudioResolverRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object PlaybackPrefetcher {
    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return

        synchronized(this) {
            if (initialized) return
            initialized = true
        }

        scope.launch {
            var lastRequestedEntryId: String? = null

            PlaybackQueueStore.state.collectLatest { state ->
                val nextEntry =
                    state.entries.getOrNull(state.currentIndex + 1)

                if (nextEntry == null) {
                    lastRequestedEntryId = null
                    PlaybackPrefetchCache.clear()
                    return@collectLatest
                }

                if (nextEntry.entryId == lastRequestedEntryId) {
                    return@collectLatest
                }

                lastRequestedEntryId = nextEntry.entryId

                val track =
                    PlaybackQueueStore.run {
                        nextEntry.toTrack()
                    }

                val originalSource =
                    track.originalStreamUrl
                        ?.takeIf(String::isNotBlank)
                        ?: track.streamUrl

                val videoId =
                    YouTubeAudioResolverRuntime
                        .videoIdFromSource(originalSource)
                        ?: return@collectLatest

                when (
                    val result =
                        YouTubeAudioResolverRuntime.resolve(
                            videoId = videoId,
                            order = PlaybackPrefetchPolicy.resolverOrder
                        )
                ) {
                    is PlaybackResolutionResult.Success -> {
                        val watchUrl =
                            PlaybackStreamResolver
                                .youtubeWatchUrlFromId(videoId)

                        val resolvedTrack =
                            track.copy(
                                streamUrl = result.stream.url,
                                downloadUrl = watchUrl,
                                originalStreamUrl = originalSource
                            )

                        ResolvedStreamRequestHeaders.put(
                            url = result.stream.url,
                            headers = result.stream.headers
                        )

                        PlaybackPrefetchCache.put(
                            track = resolvedTrack,
                            resolver = result.stream.resolver,
                            headers = result.stream.headers,
                            originalSource = originalSource
                        )
                    }

                    is PlaybackResolutionResult.VerificationRequired,
                    is PlaybackResolutionResult.Failed ->
                        PlaybackPrefetchCache.clear()
                }
            }
        }
    }
}
