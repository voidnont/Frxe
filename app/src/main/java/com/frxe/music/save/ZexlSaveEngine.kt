package com.frxe.music.save

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.frxe.music.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.coroutineContext

internal class ZexlSaveEngine(
    private val context: Context,
    baseUrl: String,
    private val apiKey: String? = null
) {
    private val baseUrl = baseUrl.trim().trimEnd('/')

    val configured: Boolean get() = baseUrl.startsWith("https://") || baseUrl.startsWith("http://")

    suspend fun save(request: SaveRequest, onState: (SaveUiState) -> Unit): SaveResult = withContext(Dispatchers.IO) {
        require(configured) { "Zexl converter endpoint is not configured" }

        onState(
            SaveUiState(
                stage = SaveStage.Validating,
                progress = 0.03f,
                message = "Waking Zexl converter",
                backend = DownloadBackend.Zexl
            )
        )
        requestJsonRetrying("/health", "GET", null)

        val startBody = JSONObject()
            .put("url", request.sourceUrl)
            .put("format", request.format.name.lowercase(Locale.US))
            .toString()
        var job = JSONObject(requestJson("/api/convert", "POST", startBody))
        val id = job.getString("id")

        while (true) {
            coroutineContext.ensureActive()
            val status = job.optString("status")
            val remoteProgress = job.optInt("progress", 0).coerceIn(0, 100)
            onState(
                SaveUiState(
                    stage = if (status == "ready") SaveStage.Downloading else SaveStage.Converting,
                    progress = if (status == "ready") 0.82f else 0.08f + (remoteProgress / 100f) * 0.70f,
                    message = when (status) {
                        "ready" -> "Zexl conversion ready"
                        "error" -> job.optString("error", "Zexl conversion failed")
                        else -> "Zexl converting $remoteProgress%"
                    },
                    backend = DownloadBackend.Zexl
                )
            )
            when (status) {
                "ready" -> break
                "error" -> throw IOException(job.optString("error", "Zexl conversion failed"))
            }
            delay(1_000)
            job = JSONObject(requestJsonRetrying("/api/jobs/$id", "GET", null))
        }

        val temp = File.createTempFile("frxe-zexl-", ".${request.format.extension}", context.cacheDir)
        try {
            downloadFileRetrying("/api/jobs/$id/file", temp) { fraction ->
                onState(
                    SaveUiState(
                        stage = SaveStage.Downloading,
                        progress = 0.80f + fraction * 0.14f,
                        message = "Downloading converted file ${(fraction * 100).toInt()}%",
                        backend = DownloadBackend.Zexl
                    )
                )
            }
            onState(
                SaveUiState(
                    stage = SaveStage.Exporting,
                    progress = 0.96f,
                    message = "Saving to Music/Frxe",
                    backend = DownloadBackend.Zexl
                )
            )
            val uri = export(temp, request)
            val result = SaveResult(
                uri = uri.toString(),
                title = request.title.ifBlank { job.optString("title").ifBlank { "Frxe export" } },
                artist = request.artist.ifBlank { "Unknown artist" },
                format = request.format
            )
            onState(
                SaveUiState(
                    stage = SaveStage.Complete,
                    progress = 1f,
                    message = "Saved to Music/Frxe",
                    savedUri = result.uri,
                    savedTitle = result.title,
                    backend = DownloadBackend.Zexl
                )
            )
            result
        } finally {
            temp.delete()
        }
    }

    fun cancel() = Unit

    private suspend fun requestJsonRetrying(
        path: String,
        method: String,
        body: String?,
        attempts: Int = 3
    ): String {
        var lastError: Throwable? = null
        repeat(attempts.coerceAtLeast(1)) { attempt ->
            coroutineContext.ensureActive()
            try {
                return requestJson(path, method, body)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastError = error
                if (attempt < attempts - 1) delay(900L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Zexl request failed")
    }

    private suspend fun downloadFileRetrying(path: String, target: File, onProgress: (Float) -> Unit) {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            coroutineContext.ensureActive()
            try {
                target.delete()
                downloadFile(path, target, onProgress)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastError = error
                if (attempt < 2) delay(900L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Zexl file download failed")
    }

    private fun requestJson(path: String, method: String, body: String?): String {
        val connection = open(path, method)
        try {
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "Zexl HTTP $status")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadFile(path: String, target: File, onProgress: (Float) -> Unit) {
        val connection = open(path, "GET")
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Zexl file download returned HTTP $status")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.buffered().use { input ->
                FileOutputStream(target).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        val fraction = total?.let { (copied.toDouble() / it.toDouble()).toFloat().coerceIn(0f, 1f) }
                            ?: (1f - 1f / (1f + copied / 2_000_000f)).coerceAtMost(0.95f)
                        onProgress(fraction)
                    }
                }
            }
            onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String): HttpURLConnection =
        (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 90_000
            readTimeout = 90_000
            setRequestProperty("User-Agent", "Frxe/${BuildConfig.VERSION_NAME}")
            apiKey?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

    private fun export(source: File, request: SaveRequest): Uri {
        val displayName = outputFileName(request.title, request.format)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, request.format.mimeType)
                put(MediaStore.Audio.Media.TITLE, request.title.ifBlank { "Frxe export" })
                put(MediaStore.Audio.Media.ARTIST, request.artist.ifBlank { "Unknown artist" })
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Frxe")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create MediaStore item")
            try {
                resolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { it.copyTo(output) } }
                    ?: error("Could not open MediaStore destination")
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }

        val root = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val directory = File(root, "Frxe").apply { mkdirs() }
        val destination = File(directory, displayName)
        source.copyTo(destination, overwrite = true)
        return Uri.fromFile(destination)
    }
}