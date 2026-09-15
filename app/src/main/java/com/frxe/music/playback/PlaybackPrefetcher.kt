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
            var lastRequestedEntryIds: List<String> = emptyList()

            PlaybackQueueStore.state.collectLatest { state ->
                val nextEntries =
                    PlaybackPrefetchWindowPolicy
                        .nextIndexes(
                            currentIndex = state.currentIndex,
                            totalCount = state.entries.size
                        )
                        .map { index -> state.entries[index] }

                if (nextEntries.isEmpty()) {
                    lastRequestedEntryIds = emptyList()
                    PlaybackPrefetchCache.clear()
                    return@collectLatest
                }

                val nextEntryIds =
                    nextEntries.map { it.entryId }

                PlaybackPrefetchCache.retain(
                    nextEntries.map { it.trackId }.toSet()
                )

                if (nextEntryIds == lastRequestedEntryIds) {
                    return@collectLatest
                }

                lastRequestedEntryIds = nextEntryIds

                for (nextEntry in nextEntries) {
                    val track =
                        PlaybackQueueStore.run {
                            nextEntry.toTrack()
                        }

                    if (PlaybackPrefetchCache.forTrack(track) != null) {
                        continue
                    }

                    val originalSource =
                        track.originalStreamUrl
                            ?.takeIf(String::isNotBlank)
                            ?: track.streamUrl

                    val videoId =
                        YouTubeAudioResolverRuntime
                            .videoIdFromSource(originalSource)

                    if (videoId == null) {
                        PlaybackPrefetchCache.remove(track.id)
                        continue
                    }

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
                            PlaybackPrefetchCache.remove(track.id)
                    }
                }
            }
        }
    }
}
