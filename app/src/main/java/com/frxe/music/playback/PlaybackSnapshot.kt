package com.frxe.music.playback

import java.nio.charset.StandardCharsets
import java.util.Base64

data class StoredMediaItem(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val artworkSeed: Int = 0,
    val downloadUrl: String? = null
)

data class PlaybackSnapshot(
    val items: List<StoredMediaItem>,
    val startIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
    val playbackSpeed: Float
)

private const val SNAPSHOT_VERSION = "frxe-playback-v1"
private val encoder = Base64.getUrlEncoder().withoutPadding()
private val decoder = Base64.getUrlDecoder()

fun encodePlaybackSnapshot(snapshot: PlaybackSnapshot): String = buildString {
    append(SNAPSHOT_VERSION)
    append('\t').append(snapshot.startIndex.coerceAtLeast(0))
    append('\t').append(snapshot.positionMs.coerceAtLeast(0L))
    append('\t').append(if (snapshot.shuffleEnabled) '1' else '0')
    append('\t').append(snapshot.repeatMode)
    append('\t').append(snapshot.playbackSpeed)
    snapshot.items.forEach { item ->
        append('\n')
        append(encodeField(item.mediaId)).append('\t')
        append(encodeField(item.uri)).append('\t')
        append(encodeField(item.title)).append('\t')
        append(encodeField(item.artist)).append('\t')
        append(encodeField(item.album)).append('\t')
        append(encodeNullableField(item.artworkUri)).append('\t')
        append(item.durationMs).append('\t')
        append(item.artworkSeed).append('\t')
        append(encodeNullableField(item.downloadUrl))
    }
}

fun decodePlaybackSnapshot(raw: String): PlaybackSnapshot? = runCatching {
    if (raw.isBlank()) return@runCatching null
    val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
    val header = lines.firstOrNull()?.split('\t') ?: return@runCatching null
    if (header.size != 6 || header[0] != SNAPSHOT_VERSION) return@runCatching null
    val items = lines.drop(1).map { line ->
        val fields = line.split('\t')
        require(fields.size == 9)
        StoredMediaItem(
            mediaId = decodeField(fields[0]),
            uri = decodeField(fields[1]),
            title = decodeField(fields[2]),
            artist = decodeField(fields[3]),
            album = decodeField(fields[4]),
            artworkUri = decodeNullableField(fields[5]),
            durationMs = fields[6].toLong().coerceAtLeast(0L),
            artworkSeed = fields[7].toInt(),
            downloadUrl = decodeNullableField(fields[8])
        )
    }.filter { it.mediaId.isNotBlank() && it.uri.isNotBlank() }
    if (items.isEmpty()) return@runCatching null
    PlaybackSnapshot(
        items = items,
        startIndex = header[1].toInt().coerceIn(0, items.lastIndex),
        positionMs = header[2].toLong().coerceAtLeast(0L),
        shuffleEnabled = header[3] == "1",
        repeatMode = header[4].toInt(),
        playbackSpeed = header[5].toFloat().coerceIn(0.5f, 2f)
    )
}.getOrNull()

private fun encodeField(value: String): String =
    encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun decodeField(value: String): String =
    String(decoder.decode(value), StandardCharsets.UTF_8)

private fun encodeNullableField(value: String?): String =
    value?.let(::encodeField) ?: "-"

private fun decodeNullableField(value: String): String? =
    if (value == "-") null else decodeField(value)
