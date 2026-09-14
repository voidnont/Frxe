package com.frxe.music.source

import com.frxe.music.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

internal object ZexlPlaybackResolver {
    private const val PLAYBACK_FORMAT = "mp3"
    private const val POLL_DELAY_MS = 1_000L
    private const val MAX_POLLS = 180

    private val baseUrl: String
        get() = ZexlPlaybackContract.normalizeBaseUrl(
            BuildConfig.ZEXL_BASE_URL
        )

    val configured: Boolean
        get() = ZexlPlaybackContract.isConfigured(baseUrl)

    suspend fun resolve(
        sourceUrl: String
    ): ResolvedAudioCandidate? =
        withContext(Dispatchers.IO) {
            if (!configured) {
                return@withContext null
            }

            val source = sourceUrl.trim()
            if (source.isEmpty()) {
                return@withContext null
            }

            val startBody = JSONObject()
                .put("url", source)
                .put("format", PLAYBACK_FORMAT)
                .toString()

            var job = JSONObject(
                requestJson(
                    path = "/api/convert",
                    method = "POST",
                    body = startBody
                )
            )

            val jobId = job
                .optString("id")
                .trim()
                .takeIf(String::isNotEmpty)
                ?: throw IOException(
                    "ZEXL returned no playback job id."
                )

            repeat(MAX_POLLS) {
                coroutineContext.ensureActive()

                when (
                    job.optString("status")
                        .trim()
                        .lowercase()
                ) {
                    "ready" -> {
                        val downloadUrl = job
                            .optString("downloadUrl")
                            .trim()
                            .takeIf {
                                it.isNotEmpty() &&
                                    !it.equals(
                                        "null",
                                        ignoreCase = true
                                    )
                            }

                        val fileUrl =
                            ZexlPlaybackContract.fileUrl(
                                baseUrl = baseUrl,
                                downloadUrl = downloadUrl,
                                jobId = jobId
                            )

                        return@withContext ResolvedAudioCandidate(
                            url = fileUrl,
                            headers =
                                ZexlPlaybackContract
                                    .authorizationHeaders(
                                        BuildConfig.ZEXL_API_KEY
                                    )
                        )
                    }

                    "error" -> {
                        throw IOException(
                            zexlErrorMessage(job)
                        )
                    }
                }

                delay(POLL_DELAY_MS)

                job = JSONObject(
                    requestJson(
                        path = "/api/jobs/$jobId",
                        method = "GET",
                        body = null
                    )
                )
            }

            throw IOException(
                "ZEXL playback conversion timed out."
            )
        }

    private fun zexlErrorMessage(
        job: JSONObject
    ): String {
        val error = job
            .optString("error")
            .trim()
            .takeIf(String::isNotEmpty)
            ?: "ZEXL could not prepare this audio."

        val code = job
            .optString("errorCode")
            .trim()
            .takeIf {
                it.isNotEmpty() &&
                    !it.equals(
                        "null",
                        ignoreCase = true
                    )
            }

        return if (code == null) {
            error
        } else {
            "$error [$code]"
        }
    }

    private fun requestJson(
        path: String,
        method: String,
        body: String?
    ): String {
        val connection = (
            URL("$baseUrl$path")
                .openConnection() as HttpURLConnection
            ).apply {
                requestMethod = method
                connectTimeout = 30_000
                readTimeout = 90_000
                instanceFollowRedirects = true
                setRequestProperty(
                    "Accept",
                    "application/json"
                )
                setRequestProperty(
                    "User-Agent",
                    "Frxe/${BuildConfig.VERSION_NAME}"
                )

                ZexlPlaybackContract
                    .authorizationHeaders(
                        BuildConfig.ZEXL_API_KEY
                    )
                    .forEach { (name, value) ->
                        setRequestProperty(name, value)
                    }
            }

        return try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
                connection.outputStream.use { output ->
                    output.write(
                        body.toByteArray(Charsets.UTF_8)
                    )
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val text = stream
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (status !in 200..299) {
                val remoteError = runCatching {
                    JSONObject(text)
                        .optString("error")
                        .trim()
                        .takeIf(String::isNotEmpty)
                }.getOrNull()

                throw IOException(
                    remoteError
                        ?: "ZEXL returned HTTP $status."
                )
            }

            text
        } finally {
            connection.disconnect()
        }
    }
}
