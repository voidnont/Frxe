package com.frxe.music.playback

import java.net.URI

private const val CATALOG_YOUTUBE_PREFIX =
    "frxe-catalog://youtube/"

/*
 * Stable internal identifier for a
 * YouTube catalog result.
 *
 * This is metadata only.
 *
 * It must be resolved into a real audio
 * URL before Media3 receives it.
 */
fun catalogMetadataUri(
    videoId: String
): String {

    return CATALOG_YOUTUBE_PREFIX +
        videoId.trim()
}

object AudioOnlyPlaybackPolicy {

    fun isPlayable(
        raw: String?
    ): Boolean {

        val value =
            raw
                ?.trim()
                .orEmpty()

        /*
         * Empty source = never playable.
         */
        if (
            value.isEmpty()
        ) {
            return false
        }

        /*
         * Frxe's catalog URI is NOT an
         * actual media URL.
         *
         * PlaybackStreamResolver must
         * resolve it first.
         */
        if (
            value.startsWith(
                CATALOG_YOUTUBE_PREFIX,
                ignoreCase = true
            )
        ) {
            return false
        }

        /*
         * Android MediaStore / local
         * content provider.
         */
        if (
            value.startsWith(
                "content://",
                ignoreCase = true
            )
        ) {
            return true
        }

        /*
         * Local file.
         */
        if (
            value.startsWith(
                "file://",
                ignoreCase = true
            )
        ) {
            return true
        }

        val uri =
            runCatching {
                URI(value)
            }
                .getOrNull()
                ?: return false

        /*
         * Network playback must use
         * HTTP/HTTPS.
         */
        val scheme =
            uri.scheme
                ?.lowercase()

        if (
            scheme !in
            setOf(
                "http",
                "https"
            )
        ) {
            return false
        }

        val host =
            uri.host
                ?.lowercase()
                ?.trimEnd('.')
                ?: return false

        /*
         * These are YouTube webpages,
         * not direct audio sources.
         *
         * They must first go through
         * PlaybackStreamResolver/NewPipe.
         */
        if (
            isYouTubePageHost(
                host
            )
        ) {
            return false
        }

        /*
         * YouTube's internal API endpoint
         * is not a media file either.
         */
        if (
            host == "youtubei.googleapis.com" ||
            host.endsWith(
                ".youtubei.googleapis.com"
            )
        ) {
            return false
        }

        /*
         * IMPORTANT:
         *
         * googlevideo.com IS allowed.
         *
         * NewPipe commonly resolves
         * YouTube audio into temporary
         * googlevideo CDN URLs.
         *
         * Blocking googlevideo here would
         * make our new resolver useless.
         */
        return true
    }

    private fun isYouTubePageHost(
        host: String
    ): Boolean {

        return host == "youtube.com" ||
            host.endsWith(
                ".youtube.com"
            ) ||
            host == "youtu.be" ||
            host.endsWith(
                ".youtu.be"
            ) ||
            host == "youtube-nocookie.com" ||
            host.endsWith(
                ".youtube-nocookie.com"
            )
    }
}