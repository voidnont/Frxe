package com.frxe.music.source

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal interface SearchProvider {
    val id: String
    suspend fun search(query: String): List<SearchHit>
}

internal class InnerTubeSearchProvider : SearchProvider {
    override val id: String = "innertube"

    override suspend fun search(query: String): List<SearchHit> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            val requestBody = JSONObject()
                .put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject()
                            .put("clientName", "WEB")
                            .put("clientVersion", WEB_CLIENT_VERSION)
                            .put("hl", "en")
                            .put("gl", "US")
                    )
                )
                .put("query", normalized)
                .put("params", "CAISAhAB")
                .toString()

            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode !in 200..299) return emptyList()

            val root = connection.inputStream.bufferedReader().use { reader ->
                JSONObject(reader.readText())
            }
            parseInnerTubeResults(root)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseInnerTubeResults(root: JSONObject): List<SearchHit> {
        val renderers = ArrayList<JSONObject>()
        collectVideoRenderers(root, renderers)
        return renderers.asSequence()
            .mapNotNull { renderer ->
                val videoId = renderer.optString("videoId").takeIf { it.length == 11 } ?: return@mapNotNull null
                val title = textFrom(renderer.optJSONObject("title")).ifBlank { "YouTube video" }
                val artist = textFrom(renderer.optJSONObject("ownerText"))
                    .ifBlank { textFrom(renderer.optJSONObject("longBylineText")) }
                    .ifBlank { "YouTube" }
                val durationText = textFrom(renderer.optJSONObject("lengthText"))
                val thumbnail = renderer.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                    ?.let(::lastThumbnailUrl)
                SearchHit(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    durationMs = parseDurationMs(durationText),
                    artworkUrl = thumbnail,
                    provider = id
                )
            }
            .distinctBy(SearchHit::videoId)
            .take(MAX_RESULTS)
            .toList()
    }

    private fun collectVideoRenderers(node: Any?, output: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("videoRenderer")?.let(output::add)
                val keys = node.keys()
                while (keys.hasNext()) {
                    collectVideoRenderers(node.opt(keys.next()), output)
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    collectVideoRenderers(node.opt(index), output)
                }
            }
        }
    }

    private fun textFrom(node: JSONObject?): String {
        if (node == null) return ""
        node.optString("simpleText").takeIf(String::isNotBlank)?.let { return it }
        val runs = node.optJSONArray("runs") ?: return ""
        return buildString {
            for (index in 0 until runs.length()) {
                append(runs.optJSONObject(index)?.optString("text").orEmpty())
            }
        }.trim()
    }

    private fun lastThumbnailUrl(thumbnails: JSONArray): String? {
        for (index in thumbnails.length() - 1 downTo 0) {
            val url = thumbnails.optJSONObject(index)?.optString("url").orEmpty()
            if (url.isNotBlank()) return url
        }
        return null
    }

    private fun parseDurationMs(value: String): Long {
        val parts = value.trim().split(':').mapNotNull(String::toLongOrNull)
        if (parts.isEmpty()) return 0L
        var seconds = 0L
        parts.forEach { part -> seconds = seconds * 60L + part }
        return seconds * 1_000L
    }

    private companion object {
        const val ENDPOINT = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
        const val WEB_CLIENT_VERSION = "2.20250925.01.00"
        const val MAX_RESULTS = 18
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0 Mobile Safari/537.36"
    }
}

internal class NewPipeSearchProvider : SearchProvider {
    override val id: String = "newpipe"

    override suspend fun search(query: String): List<SearchHit> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        ensureInitialized()

        val service = ServiceList.YouTube
        val queryHandler = service.searchQHFactory.fromQuery(normalized, emptyList(), "")
        val extractor = service.getSearchExtractor(queryHandler)
        extractor.fetchPage()

        return extractor.initialPage.items
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val videoId = extractYouTubeVideoId(item.url) ?: return@mapNotNull null
                SearchHit(
                    videoId = videoId,
                    title = item.name.ifBlank { "YouTube video" },
                    artist = runCatching { item.uploaderName }.getOrNull().orEmpty().ifBlank { "YouTube" },
                    durationMs = runCatching { item.duration.coerceAtLeast(0L) * 1_000L }.getOrDefault(0L),
                    artworkUrl = runCatching { item.thumbnails.lastOrNull()?.url }.getOrNull(),
                    provider = id
                )
            }
            .distinctBy(SearchHit::videoId)
            .take(MAX_RESULTS)
            .toList()
    }

    private fun ensureInitialized() = FrxeNewPipeRuntime.initialize()

    private companion object {
        const val MAX_RESULTS = 18
    }
}

internal class YtDlpSearchProvider(application: Application) : SearchProvider {
    override val id: String = "yt-dlp"
    private val app = application.applicationContext

    override suspend fun search(query: String): List<SearchHit> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        ensureInitialized()

        val request = YoutubeDLRequest("ytsearch${MAX_RESULTS}:$normalized").apply {
            addOption("--flat-playlist")
            addOption("--skip-download")
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--no-playlist")
        }
        val response = YoutubeDL.getInstance().execute(request)
        val root = JSONObject(response.out)
        val entries = root.optJSONArray("entries") ?: return emptyList()

        return buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val videoId = entry.optString("id").takeIf { it.length == 11 } ?: continue
                val durationSeconds = entry.optDouble("duration", 0.0).takeIf { it.isFinite() } ?: 0.0
                add(
                    SearchHit(
                        videoId = videoId,
                        title = entry.optString("title", "YouTube video"),
                        artist = entry.optString("channel")
                            .ifBlank { entry.optString("uploader") }
                            .ifBlank { "YouTube" },
                        durationMs = (durationSeconds * 1_000.0).toLong().coerceAtLeast(0L),
                        artworkUrl = entry.optString("thumbnail").takeIf(String::isNotBlank)
                            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                        provider = id
                    )
                )
            }
        }.distinctBy(SearchHit::videoId)
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            YoutubeDL.getInstance().init(app)
            initialized = true
        }
    }

    private companion object {
        const val MAX_RESULTS = 12
        val initLock = Any()

        @Volatile
        var initialized: Boolean = false
    }
}
