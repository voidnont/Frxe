package com.frxe.music.source

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe

internal object YouTubeAudioResolverRuntime {
    private const val INNERTUBE_WEB_KEY =
        "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val WEB_CLIENT_VERSION =
        "2.20260708.00.00"
    private const val INNERTUBE_PLAYER_ENDPOINT =
        "https://www.youtube.com/youtubei/v1/player?prettyPrint=false&key=$INNERTUBE_WEB_KEY"

    suspend fun resolve(
        videoId: String,
        order: List<PlaybackResolverKind>,
        onAttempt: (PlaybackResolverKind) -> Unit = {}
    ): PlaybackResolutionResult = withContext(Dispatchers.IO) {
        val watchUrl = youtubeWatchUrlFromId(videoId)

        val result = PlaybackResolverChain(
            order.map { kind ->
                kind to suspend {
                    onAttempt(kind)
                    when (kind) {
                        PlaybackResolverKind.NewPipe ->
                            resolveWithNewPipe(watchUrl)

                        PlaybackResolverKind.InnerTube ->
                            resolveWithInnerTube(
                                videoId = videoId,
                                watchUrl = watchUrl
                            )

                        PlaybackResolverKind.YtDlp ->
                            resolveWithYtDlp(watchUrl)
                    }
                }
            }
        ).resolve()

        val attempts =
            when (result) {
                is PlaybackResolutionResult.Success ->
                    result.attempts

                is PlaybackResolutionResult.VerificationRequired ->
                    result.attempts

                is PlaybackResolutionResult.Failed ->
                    result.attempts
            }

        attempts.forEach { attempt ->
            attempt.errorMessage
                ?.takeIf(String::isNotBlank)
                ?.let { message ->
                    ResolverDiagnosticsStore.recordFailure(
                        resolver = attempt.resolver,
                        message = message
                    )
                }
        }

        if (result is PlaybackResolutionResult.Success) {
            ResolverDiagnosticsStore.recordSuccess(
                result.stream.resolver
            )
        }

        result
    }

    fun videoIdFromSource(raw: String?): String? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isBlank()) return null

        PlaybackStreamResolver.youtubeVideoId(normalized)
            ?.let { return it }

        val uri = runCatching {
            URI(normalized)
        }.getOrNull() ?: return null

        val host = uri.host
            ?.lowercase()
            ?.trimEnd('.')
            ?: return null

        if (
            host == "youtu.be" ||
            host.endsWith(".youtu.be")
        ) {
            return uri.path
                ?.trim('/')
                ?.substringBefore('/')
                ?.takeIf(::isValidVideoId)
        }

        if (
            host != "youtube.com" &&
            !host.endsWith(".youtube.com") &&
            host != "youtube-nocookie.com" &&
            !host.endsWith(".youtube-nocookie.com")
        ) {
            return null
        }

        val path = uri.path.orEmpty()

        if (path == "/watch") {
            return uri.rawQuery
                ?.split('&')
                ?.asSequence()
                ?.mapNotNull { pair ->
                    val parts = pair.split('=', limit = 2)
                    if (
                        parts.size != 2 ||
                        parts[0] != "v"
                    ) {
                        null
                    } else {
                        runCatching {
                            URLDecoder.decode(
                                parts[1],
                                Charsets.UTF_8.name()
                            )
                        }.getOrNull()
                    }
                }
                ?.firstOrNull(::isValidVideoId)
        }

        val pathVideoId = path
            .trim('/')
            .split('/')
            .let { parts ->
                when {
                    parts.size >= 2 &&
                        parts[0] in setOf(
                            "shorts",
                            "embed",
                            "live"
                        ) -> parts[1]

                    else -> null
                }
            }

        return pathVideoId
            ?.takeIf(::isValidVideoId)
    }

    fun youtubeWatchUrlFromId(
        videoId: String
    ): String =
        "https://www.youtube.com/watch?v=$videoId"

    private fun resolveWithNewPipe(
        watchUrl: String
    ): ResolvedAudioCandidate? {
        FrxeNewPipeRuntime.initialize()

        val service = NewPipe.getServiceByUrl(watchUrl)
        val extractor = service.getStreamExtractor(watchUrl)
        extractor.fetchPage()

        val url = extractor.audioStreams
            .asSequence()
            .sortedByDescending { stream ->
                runCatching {
                    stream.averageBitrate
                }.getOrDefault(0)
            }
            .mapNotNull { stream ->
                runCatching {
                    stream.content
                }
                    .getOrNull()
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
            }
            .firstOrNull(::isHttpMediaUrl)
            ?: return null

        return ResolvedAudioCandidate(
            url = url,
            headers = mapOf(
                "User-Agent" to YOUTUBE_WEB_USER_AGENT,
                "Referer" to watchUrl
            )
        )
    }

    private fun resolveWithInnerTube(
        videoId: String,
        watchUrl: String
    ): ResolvedAudioCandidate? {
        val connection = (
            URL(INNERTUBE_PLAYER_ENDPOINT)
                .openConnection() as HttpURLConnection
            ).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
                setRequestProperty(
                    "Origin",
                    "https://www.youtube.com"
                )
                setRequestProperty(
                    "User-Agent",
                    YOUTUBE_WEB_USER_AGENT
                )
                setRequestProperty(
                    "X-YouTube-Client-Name",
                    "1"
                )
                setRequestProperty(
                    "X-YouTube-Client-Version",
                    WEB_CLIENT_VERSION
                )
            }

        return try {
            val requestBody = JSONObject()
                .put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject()
                            .put("clientName", "WEB")
                            .put(
                                "clientVersion",
                                WEB_CLIENT_VERSION
                            )
                            .put("hl", "en")
                            .put("gl", "US")
                    )
                )
                .put("videoId", videoId)
                .put(
                    "playbackContext",
                    JSONObject().put(
                        "contentPlaybackContext",
                        JSONObject().put(
                            "html5Preference",
                            "HTML5_PREF_WANTS"
                        )
                    )
                )
                .toString()

            connection.outputStream.use { output ->
                output.write(
                    requestBody.toByteArray(Charsets.UTF_8)
                )
            }

            if (connection.responseCode !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }.getOrNull().orEmpty()

                throw IllegalStateException(
                    "InnerTube HTTP ${connection.responseCode}: $errorBody"
                )
            }

            val root = connection.inputStream
                .bufferedReader()
                .use { reader ->
                    JSONObject(reader.readText())
                }

            val playability = root
                .optJSONObject("playabilityStatus")
            val status = playability
                ?.optString("status")
                .orEmpty()

            if (status != "OK") {
                val reason = playability
                    ?.optString("reason")
                    .orEmpty()
                throw IllegalStateException(
                    listOf(
                        "InnerTube",
                        status,
                        reason
                    )
                        .filter(String::isNotBlank)
                        .joinToString(": ")
                )
            }

            val streamingData = root
                .optJSONObject("streamingData")
                ?: throw IllegalStateException(
                    "InnerTube returned no streaming data."
                )

            val url = findBestDirectAudioUrl(
                streamingData.optJSONArray(
                    "adaptiveFormats"
                )
            ) ?: findBestDirectAudioUrl(
                streamingData.optJSONArray("formats")
            ) ?: return null

            ResolvedAudioCandidate(
                url = url,
                headers = mapOf(
                    "User-Agent" to YOUTUBE_WEB_USER_AGENT,
                    "Referer" to watchUrl,
                    "Origin" to "https://www.youtube.com"
                )
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveWithYtDlp(
        watchUrl: String
    ): ResolvedAudioCandidate? {
        val attempts = listOf(
            YtDlpAttempt(
                selector =
                    "bestaudio[ext=m4a]/bestaudio/best",
                playerClient = null
            ),
            YtDlpAttempt(
                selector = "bestaudio/best",
                playerClient = "web_safari"
            ),
            YtDlpAttempt(
                selector = "bestaudio/best",
                playerClient = "android_vr"
            )
        )

        var lastError: Throwable? = null

        for (attempt in attempts) {
            try {
                val request = YoutubeDLRequest(watchUrl)
                    .apply {
                        addOption("--no-playlist")
                        addOption("--no-warnings")
                        addOption(
                            "-f",
                            attempt.selector
                        )
                        attempt.playerClient
                            ?.let { client ->
                                addOption(
                                    "--extractor-args",
                                    "youtube:player_client=$client"
                                )
                            }
                    }

                val info = YoutubeDL
                    .getInstance()
                    .getInfo(request)

                val url = info.url
                    ?.trim()
                    ?.takeIf(::isHttpMediaUrl)

                if (url != null) {
                    val headers = buildMap {
                        put(
                            "User-Agent",
                            YOUTUBE_WEB_USER_AGENT
                        )
                        put(
                            "Referer",
                            watchUrl
                        )
                        info.httpHeaders
                            ?.forEach { (name, value) ->
                                if (
                                    name.isNotBlank() &&
                                    value.isNotBlank()
                                ) {
                                    put(name, value)
                                }
                            }
                    }

                    return ResolvedAudioCandidate(
                        url = url,
                        headers = headers
                    )
                }
            } catch (error: Throwable) {
                lastError = error
            }
        }

        lastError?.let { throw it }
        return null
    }

    private fun findBestDirectAudioUrl(
        formats: JSONArray?
    ): String? {
        if (formats == null) return null

        var bestUrl: String? = null
        var bestBitrate = -1L

        for (index in 0 until formats.length()) {
            val format = formats
                .optJSONObject(index)
                ?: continue
            val mimeType = format
                .optString("mimeType")

            if (
                !mimeType.startsWith(
                    "audio/",
                    ignoreCase = true
                )
            ) {
                continue
            }

            val url = format
                .optString("url")
                .trim()
                .takeIf(::isHttpMediaUrl)
                ?: continue
            val bitrate = format
                .optLong("bitrate", 0L)

            if (bitrate > bestBitrate) {
                bestBitrate = bitrate
                bestUrl = url
            }
        }

        return bestUrl
    }

    private fun isValidVideoId(
        value: String
    ): Boolean =
        value.length == 11

    private fun isHttpMediaUrl(
        value: String
    ): Boolean {
        val normalized = value.trim()
        return normalized.startsWith(
            "https://",
            ignoreCase = true
        ) || normalized.startsWith(
            "http://",
            ignoreCase = true
        )
    }

    private data class YtDlpAttempt(
        val selector: String,
        val playerClient: String?
    )
}
