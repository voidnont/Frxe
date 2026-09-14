package com.frxe.music.save

import com.frxe.music.source.PlaybackStreamResolver
import java.net.URI

enum class DownloadBackend(
    val label: String
) {
    Direct("Direct"),
    InnerTube("InnerTube"),
    NewPipe("NewPipe"),
    YtDlp("yt-dlp"),
    Zexl("Zexl"),
    Cobalt("Cobalt")
}

object DownloadRoutePolicy {

    fun backendsFor(
        sourceUrl: String
    ): List<DownloadBackend> {
        val value = sourceUrl.trim()

        if (value.isEmpty()) {
            return emptyList()
        }

        if (
            PlaybackStreamResolver.isCatalogTrack(value) ||
            isYouTubePageUrl(value)
        ) {
            return listOf(
                DownloadBackend.InnerTube,
                DownloadBackend.NewPipe,
                DownloadBackend.YtDlp,
                DownloadBackend.Zexl,
                DownloadBackend.Cobalt
            )
        }

        if (isDirectDownloadUrl(value)) {
            return listOf(
                DownloadBackend.Direct,
                DownloadBackend.Zexl,
                DownloadBackend.Cobalt
            )
        }

        return listOf(
            DownloadBackend.Zexl,
            DownloadBackend.Cobalt
        )
    }

    fun canTryNewPipe(
        sourceUrl: String
    ): Boolean {
        val value = sourceUrl.trim()

        if (value.isEmpty()) {
            return false
        }

        if (
            PlaybackStreamResolver.isCatalogTrack(value) ||
            isYouTubePageUrl(value)
        ) {
            return true
        }

        val uri = runCatching {
            URI(value)
        }.getOrNull() ?: return false

        return uri.scheme
            ?.lowercase() in setOf(
                "http",
                "https"
            )
    }

    fun requiresDownloadSource(
        sourceUrl: String?
    ): Boolean =
        sourceUrl.isNullOrBlank()
}

fun automaticDownloadSource(
    downloadUrl: String?,
    streamUrl: String?
): String? {
    val downloadCandidate = downloadUrl
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    if (downloadCandidate != null) {
        return PlaybackStreamResolver
            .youtubeWatchUrl(downloadCandidate)
            ?: downloadCandidate
    }

    val streamCandidate = streamUrl
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null

    return PlaybackStreamResolver
        .youtubeWatchUrl(streamCandidate)
        ?: streamCandidate
}

fun isYouTubePageUrl(
    raw: String?
): Boolean {
    val value = raw
        ?.trim()
        .orEmpty()

    if (value.isEmpty()) {
        return false
    }

    val uri = runCatching {
        URI(value)
    }.getOrNull() ?: return false

    val host = uri.host
        ?.lowercase()
        ?.trimEnd('.')
        ?: return false

    return host == "youtube.com" ||
        host.endsWith(".youtube.com") ||
        host == "youtu.be" ||
        host.endsWith(".youtu.be") ||
        host == "youtube-nocookie.com" ||
        host.endsWith(".youtube-nocookie.com")
}

fun isDirectDownloadUrl(
    raw: String?
): Boolean {
    val value = raw
        ?.trim()
        .orEmpty()

    if (value.isEmpty()) {
        return false
    }

    if (isYouTubePageUrl(value)) {
        return false
    }

    val uri = runCatching {
        URI(value)
    }.getOrNull() ?: return false

    val scheme = uri.scheme
        ?.lowercase()

    if (
        scheme !in setOf(
            "http",
            "https"
        )
    ) {
        return false
    }

    return !uri.host.isNullOrBlank()
}
