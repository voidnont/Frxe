package com.frxe.music.save

import java.net.URI

object SaveUrlPolicy {
    fun isAllowedDirectMediaUrl(raw: String): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return true
    }
}

fun outputFileName(title: String, format: SaveFormat): String {
    val cleaned = title
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim('.', ' ')
        .ifBlank { "Frxe export" }
        .take(120)
    return "$cleaned.${format.extension}"
}

fun buildFfmpegArguments(
    inputPath: String,
    outputPath: String,
    format: SaveFormat,
    quality: SaveQuality,
    title: String,
    artist: String
): List<String> {
    val common = mutableListOf(
        "-y",
        "-i", inputPath,
        "-vn",
        "-map_metadata", "-1",
        "-metadata", "title=$title",
        "-metadata", "artist=$artist"
    )

    when (format) {
        SaveFormat.MP3 -> {
            val bitrate = when (quality) {
                SaveQuality.Mp3K128 -> "128k"
                SaveQuality.Mp3K192 -> "192k"
                SaveQuality.Mp3K256 -> "256k"
                SaveQuality.Mp3K320 -> "320k"
                else -> "320k"
            }
            common += listOf("-c:a", "libmp3lame", "-b:a", bitrate)
        }
        SaveFormat.FLAC -> {
            val sampleRate = if (quality == SaveQuality.Lossless44k) "44100" else "48000"
            common += listOf("-c:a", "flac", "-compression_level", "8", "-ar", sampleRate)
        }
        SaveFormat.WAV -> {
            val sampleRate = if (quality == SaveQuality.Lossless44k) "44100" else "48000"
            common += listOf("-c:a", "pcm_s24le", "-ar", sampleRate)
        }
    }
    common += outputPath
    return common
}

fun List<String>.asFfmpegCommand(): String = joinToString(" ") { argument ->
    if (argument.none { it.isWhitespace() || it == '\'' || it == '"' }) argument
    else "'${argument.replace("'", "'\\''")}'"
}
