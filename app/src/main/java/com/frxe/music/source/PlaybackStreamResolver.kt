package com.frxe.music.source

import com.frxe.music.model.Track
import com.frxe.music.playback.AudioOnlyPlaybackPolicy
import com.frxe.music.playback.PlaybackPrefetchCache
import com.frxe.music.playback.ResolvedStreamRequestHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaybackStreamResolver {
    suspend fun resolve(
        track: Track
    ): Track? =
        withContext(Dispatchers.IO) {
            resolveOnIo(track)
        }

    private suspend fun resolveOnIo(
        track: Track
    ): Track? {
        PlaybackResolutionMonitor.resolving(track.id)

        if (AudioOnlyPlaybackPolicy.isPlayable(track.streamUrl)) {
            PlaybackResolutionMonitor.resolved(
                trackId = track.id,
                resolver = null
            )
            return track
        }

        val videoId =
            YouTubeAudioResolverRuntime
                .videoIdFromSource(track.streamUrl)

        if (videoId == null) {
            PlaybackResolutionMonitor.failed(
                trackId = track.id,
                message =
                    "This track does not contain a resolvable audio source."
            )
            return null
        }

        val prefetched =
            PlaybackPrefetchCache.forTrack(track)

        if (prefetched != null) {
            ResolvedStreamRequestHeaders.put(
                url = prefetched.track.streamUrl,
                headers = prefetched.headers
            )

            PlaybackResolutionMonitor.resolved(
                trackId = track.id,
                resolver = prefetched.resolver
            )

            return prefetched.track
        }

        val watchUrl = youtubeWatchUrlFromId(videoId)

        return when (
            val resolution =
                YouTubeAudioResolverRuntime.resolve(
                    videoId = videoId,
                    order =
                        listOf(
                            PlaybackResolverKind.NewPipe,
                            PlaybackResolverKind.InnerTube,
                            PlaybackResolverKind.YtDlp
                        )
                )
        ) {
            is PlaybackResolutionResult.Success -> {
                if (!PlaybackResolutionMonitor.isCurrent(track.id)) {
                    return null
                }

                ResolvedStreamRequestHeaders.put(
                    url = resolution.stream.url,
                    headers = resolution.stream.headers
                )

                PlaybackResolutionMonitor.resolved(
                    trackId = track.id,
                    resolver = resolution.stream.resolver
                )

                track.copy(
                    streamUrl = resolution.stream.url,
                    downloadUrl = watchUrl,
                    originalStreamUrl =
                        track.originalStreamUrl
                            ?: track.streamUrl
                )
            }

            is PlaybackResolutionResult.VerificationRequired -> {
                PlaybackResolutionMonitor.verificationRequired(
                    trackId = track.id,
                    challenge = resolution.challenge
                )
                null
            }

            is PlaybackResolutionResult.Failed -> {
                PlaybackResolutionMonitor.failed(
                    trackId = track.id,
                    message = resolution.message
                )
                null
            }
        }
    }

    companion object {
        private const val YOUTUBE_CATALOG_PREFIX =
            "frxe-catalog://youtube/"

        fun youtubeVideoId(
            value: String?
        ): String? {
            val uri = value?.trim().orEmpty()

            if (
                !uri.startsWith(
                    YOUTUBE_CATALOG_PREFIX,
                    ignoreCase = true
                )
            ) {
                return null
            }

            val videoId =
                uri.substring(YOUTUBE_CATALOG_PREFIX.length)
                    .trim()

            return videoId.takeIf { it.length == 11 }
        }

        fun youtubeWatchUrl(
            value: String?
        ): String? {
            val videoId =
                youtubeVideoId(value)
                    ?: YouTubeAudioResolverRuntime
                        .videoIdFromSource(value)
                    ?: return null

            return youtubeWatchUrlFromId(videoId)
        }

        fun youtubeWatchUrlFromId(
            videoId: String
        ): String =
            YouTubeAudioResolverRuntime
                .youtubeWatchUrlFromId(videoId)

        fun isCatalogTrack(
            value: String?
        ): Boolean =
            youtubeVideoId(value) != null
    }
}
