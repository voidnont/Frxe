package com.frxe.music.model

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata


enum class CanvasMode { Liquid, Pulse, Minimal }

enum class FrxeRepeatMode { Off, All, One }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val durationMs: Long,
    val artworkSeed: Int,
    val artworkUrl: String? = null,
    val downloadUrl: String? = null,
    val originalStreamUrl: String? = null
) {
    fun toMediaItem(): MediaItem {
        val mediaUri = Uri.parse(streamUrl)
        val extras = Bundle().apply {
            putLong(EXTRA_DURATION_MS, durationMs)
            putInt(EXTRA_ARTWORK_SEED, artworkSeed)
            downloadUrl?.let { putString(EXTRA_DOWNLOAD_URL, it) }
            originalStreamUrl?.let {
                putString(EXTRA_ORIGINAL_STREAM_URL, it)
            }
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(mediaUri)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(mediaUri)
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setExtras(extras)
                    .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
            )
            .build()
    }
}

data class TimedLyric(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class HomeSection(
    val title: String,
    val subtitle: String? = null,
    val tracks: List<Track>
)

data class PlayerUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Int = 0,
    val queue: List<Track> = emptyList(),
    val volume: Float = 1f,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: FrxeRepeatMode = FrxeRepeatMode.Off,
    val sleepTimerRemainingMs: Long = 0L,
    val canvasMode: CanvasMode = CanvasMode.Liquid,
    val autoDjEnabled: Boolean = true
)

const val EXTRA_DURATION_MS = "frxe.duration_ms"
const val EXTRA_ARTWORK_SEED = "frxe.artwork_seed"
const val EXTRA_DOWNLOAD_URL = "frxe.download_url"
const val EXTRA_ORIGINAL_STREAM_URL = "frxe.original_stream_url"
