package com.frxe.music.source

import com.frxe.music.model.Track
import com.frxe.music.playback.AudioOnlyPlaybackPolicy
import com.frxe.music.playback.PlaybackPrefetchCache
import com.frxe.music.playback.ResolvedStreamRequestHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PlaybackResolverOrder {
    val local: List<PlaybackResolverKind> = listOf(
        PlaybackResolverKind.YtDlp,
        PlaybackResolverKind.InnerTube,
        PlaybackResolverKind.NewPipe
    )
}

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

        DownloadedTrackRegistry
            .localTrackFor(track)
            ?.let { localTrack ->
                PlaybackResolutionMonitor.resolved(
                    trackId = track.id,
                    resolver = null
                )
                return localTrack
            }

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
        val localResolution =
            YouTubeAudioResolverRuntime.resolve(
                videoId = videoId,
                order = PlaybackResolverOrder.local
            )

        return when (localResolution) {
            is PlaybackResolutionResult.Success -> {
                if (!PlaybackResolutionMonitor.isCurrent(track.id)) {
                    return null
                }

                ResolvedStreamRequestHeaders.put(
                    url = localResolution.stream.url,
                    headers = localResolution.stream.headers
                )

                PlaybackResolutionMonitor.resolved(
                    trackId = track.id,
                    resolver = localResolution.stream.resolver
                )

                track.copy(
                    streamUrl = localResolution.stream.url,
                    downloadUrl = watchUrl,
                    originalStreamUrl =
                        track.originalStreamUrl
                            ?: track.streamUrl
                )
            }

            is PlaybackResolutionResult.VerificationRequired -> {
                PlaybackResolutionMonitor.verificationRequired(
                    trackId = track.id,
                    challenge = localResolution.challenge
                )
                null
            }

            is PlaybackResolutionResult.Failed ->
                resolveWithZexlFallback(
                    track = track,
                    watchUrl = watchUrl,
                    localFailure = localResolution.message
                )
        }
    }

    private suspend fun resolveWithZexlFallback(
        track: Track,
        watchUrl: String,
        localFailure: String
    ): Track? {
        if (!ZexlPlaybackResolver.configured) {
            PlaybackResolutionMonitor.failed(
                trackId = track.id,
                message = localFailure
            )
            return null
        }

        PlaybackResolutionMonitor.resolvingFallback(
            trackId = track.id,
            fallbackName = "ZEXL"
        )

        return try {
            val candidate =
                ZexlPlaybackResolver.resolve(watchUrl)

            if (candidate == null) {
                PlaybackResolutionMonitor.failed(
                    trackId = track.id,
                    message = localFailure
                )
                return null
            }

            if (!PlaybackResolutionMonitor.isCurrent(track.id)) {
                return null
            }

            ResolvedStreamRequestHeaders.put(
                url = candidate.url,
                headers = candidate.headers
            )

            PlaybackResolutionMonitor.resolvedFallback(
                trackId = track.id,
                fallbackName = "ZEXL"
            )

            track.copy(
                streamUrl = candidate.url,
                downloadUrl = watchUrl,
                originalStreamUrl =
                    track.originalStreamUrl
                        ?: track.streamUrl
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val fallbackMessage = error.message
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(180)
                ?.takeIf(String::isNotBlank)
                ?: "ZEXL backup failed."

            PlaybackResolutionMonitor.failed(
                trackId = track.id,
                message =
                    "Local resolvers failed and ZEXL backup could not prepare this track. $fallbackMessage"
            )
            null
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
