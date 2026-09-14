package com.frxe.music.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.frxe.music.model.EXTRA_ARTWORK_SEED
import com.frxe.music.model.EXTRA_DOWNLOAD_URL
import com.frxe.music.model.EXTRA_DURATION_MS
import com.frxe.music.model.EXTRA_ORIGINAL_STREAM_URL
import com.frxe.music.model.Track

class PlaybackStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(player: Player) {
        if (player.mediaItemCount <= 0) return
        val items = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).toStoredMediaItem()
        }
        if (items.isEmpty()) return
        val snapshot = PlaybackSnapshot(
            items = items,
            startIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            playbackSpeed = player.playbackParameters.speed
        )
        preferences.edit().putString(KEY_SNAPSHOT, encodePlaybackSnapshot(snapshot)).apply()
    }

    fun restore(): PlaybackSnapshot? =
        preferences.getString(KEY_SNAPSHOT, null)?.let(::decodePlaybackSnapshot)

    fun mediaItems(snapshot: PlaybackSnapshot): List<MediaItem> = snapshot.items.map { stored ->
        stored.toTrack().toMediaItem()
    }

    fun trackForRestore(stored: StoredMediaItem): Track =
        stored.toTrack()

    fun trackFromMediaItem(mediaItem: MediaItem): Track? {
        val currentUri = mediaItem.requestMetadata.mediaUri?.toString()
            ?: return null

        val extras = mediaItem.mediaMetadata.extras
        val explicitOriginal = extras?.getString(EXTRA_ORIGINAL_STREAM_URL)
        val source = explicitOriginal
            ?.takeIf(String::isNotBlank)
            ?: PlaybackPersistencePolicy.sourceForRestore(
                mediaId = mediaItem.mediaId,
                storedUri = currentUri
            )

        return Track(
            id = mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            album = mediaItem.mediaMetadata.albumTitle?.toString().orEmpty(),
            streamUrl = source,
            durationMs = extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L,
            artworkSeed = extras?.getInt(EXTRA_ARTWORK_SEED, mediaItem.mediaId.hashCode())
                ?: mediaItem.mediaId.hashCode(),
            artworkUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
            downloadUrl = extras?.getString(EXTRA_DOWNLOAD_URL),
            originalStreamUrl = source.takeIf {
                it.startsWith("frxe-catalog://", ignoreCase = true)
            }
        )
    }

    private fun StoredMediaItem.toTrack(): Track {
        val source = PlaybackPersistencePolicy.sourceForRestore(
            mediaId = mediaId,
            storedUri = uri
        )

        return Track(
            id = mediaId,
            title = title,
            artist = artist,
            album = album,
            streamUrl = source,
            durationMs = durationMs,
            artworkSeed = artworkSeed,
            artworkUrl = artworkUri,
            downloadUrl = downloadUrl,
            originalStreamUrl = source.takeIf {
                it.startsWith("frxe-catalog://", ignoreCase = true)
            }
        )
    }

    private fun MediaItem.toStoredMediaItem(): StoredMediaItem? {
        val currentUri = requestMetadata.mediaUri?.toString() ?: return null
        val originalUri = mediaMetadata.extras?.getString(EXTRA_ORIGINAL_STREAM_URL)
        val persistentUri = PlaybackPersistencePolicy.uriForStorage(
            currentUri = currentUri,
            originalUri = originalUri
        )

        return StoredMediaItem(
            mediaId = mediaId,
            uri = persistentUri,
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString().orEmpty(),
            album = mediaMetadata.albumTitle?.toString().orEmpty(),
            artworkUri = mediaMetadata.artworkUri?.toString(),
            durationMs = mediaMetadata.extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L,
            artworkSeed = mediaMetadata.extras?.getInt(EXTRA_ARTWORK_SEED, mediaId.hashCode()) ?: mediaId.hashCode(),
            downloadUrl = mediaMetadata.extras?.getString(EXTRA_DOWNLOAD_URL)
        )
    }

    companion object {
        private const val PREFS = "frxe_playback_state"
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
