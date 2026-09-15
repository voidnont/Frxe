package com.frxe.music.source

import android.content.Context
import android.net.Uri
import com.frxe.music.model.Track
import com.frxe.music.playback.PreferredPlaybackSource
import java.io.File

internal object DownloadedTrackIdentity {
    fun trackIdForSource(
        sourceUrl: String?
    ): String? =
        YouTubeAudioResolverRuntime
            .videoIdFromSource(sourceUrl)
            ?.let { videoId ->
                "yt-$videoId"
            }
}

internal object DownloadedTrackRegistry {
    private const val PREFS = "frxe_downloaded_track_sources"
    private const val KEY_PREFIX = "track."

    @Volatile
    private var ready = false

    private lateinit var appContext: Context

    fun initialize(
        context: Context
    ) {
        if (ready) return

        synchronized(this) {
            if (ready) return
            appContext = context.applicationContext
            ready = true
        }
    }

    fun register(
        sourceUrl: String?,
        localUri: String?
    ) {
        if (!ready) return

        val trackId =
            DownloadedTrackIdentity
                .trackIdForSource(sourceUrl)
                ?: return

        val normalizedLocalUri =
            localUri
                ?.trim()
                ?.takeIf(
                    PreferredPlaybackSource::isPersistentLocal
                )
                ?: return

        preferences()
            .edit()
            .putString(
                key(trackId),
                normalizedLocalUri
            )
            .apply()
    }

    fun localTrackFor(
        track: Track
    ): Track? {
        if (!ready) return null

        val localUri = preferences()
            .getString(
                key(track.id),
                null
            )
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null

        if (!isAccessible(localUri)) {
            preferences()
                .edit()
                .remove(key(track.id))
                .apply()
            return null
        }

        val localCandidate = track.copy(
            streamUrl = localUri,
            downloadUrl = track.downloadUrl
                ?: catalogWatchUrl(track.id),
            originalStreamUrl = stableOriginalSource(track)
        )

        return PreferredPlaybackSource.choose(
            requested = track,
            candidates = listOf(localCandidate)
        ).takeIf { preferred ->
            preferred.streamUrl == localUri
        }
    }

    private fun stableOriginalSource(
        track: Track
    ): String? {
        track.originalStreamUrl
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return it }

        val videoId = track.id
            .removePrefix("yt-")
            .takeIf { id ->
                track.id.startsWith("yt-") &&
                    id.length == 11
            }

        if (videoId != null) {
            return "frxe-catalog://youtube/$videoId"
        }

        return track.streamUrl
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private fun catalogWatchUrl(
        trackId: String
    ): String? {
        if (!trackId.startsWith("yt-")) return null

        val videoId = trackId
            .removePrefix("yt-")
            .takeIf { it.length == 11 }
            ?: return null

        return YouTubeAudioResolverRuntime
            .youtubeWatchUrlFromId(videoId)
    }

    private fun isAccessible(
        localUri: String
    ): Boolean =
        runCatching {
            val uri = Uri.parse(localUri)

            when (uri.scheme?.lowercase()) {
                "content" ->
                    appContext.contentResolver
                        .openAssetFileDescriptor(
                            uri,
                            "r"
                        )
                        ?.use { true }
                        ?: false

                "file" ->
                    uri.path
                        ?.let(::File)
                        ?.isFile == true

                else -> false
            }
        }.getOrDefault(false)

    private fun preferences() =
        appContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

    private fun key(
        trackId: String
    ): String =
        KEY_PREFIX + trackId
}
