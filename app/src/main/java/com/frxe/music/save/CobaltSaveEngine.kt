package com.frxe.music.save

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class CobaltSaveEngine(
    context: Context,
    baseUrl: String,
    private val apiKey: String? = null
) {
    private val baseUrl =
        baseUrl.trim().trimEnd('/')

    private val local =
        FrxeSaveEngine(context)

    val configured: Boolean
        get() = baseUrl.startsWith("https://") ||
            baseUrl.startsWith("http://")

    suspend fun save(
        request: SaveRequest,
        onState: (SaveUiState) -> Unit
    ): SaveResult = withContext(Dispatchers.IO) {
        require(configured) {
            "Cobalt endpoint is not configured."
        }

        onState(
            SaveUiState(
                stage = SaveStage.Validating,
                progress = 0.02f,
                message = "Trying Cobalt…",
                backend = DownloadBackend.Cobalt
            )
        )

        val mediaUrl = resolveAudioUrl(
            request.sourceUrl
        )

        onState(
            SaveUiState(
                stage = SaveStage.Validating,
                progress = 0.04f,
                message = "Cobalt audio source ready",
                backend = DownloadBackend.Cobalt
            )
        )

        local.save(
            request.copy(
                sourceUrl = mediaUrl
            )
        ) { state ->
            onState(
                state.copy(
                    backend = DownloadBackend.Cobalt
                )
            )
        }
    }

    fun cancel() {
        local.cancel()
    }

    private fun resolveAudioUrl(
        sourceUrl: String
    ): String {
        val connection = (
            URL("$baseUrl/")
                .openConnection() as HttpURLConnection
            ).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty(
                    "Accept",
                    "application/json"
                )
                setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                apiKey
                    ?.takeIf(String::isNotBlank)
                    ?.let { key ->
                        setRequestProperty(
                            "Authorization",
                            "Api-Key $key"
                        )
                    }
            }

        return try {
            val body = JSONObject()
                .put("url", sourceUrl)
                .put("downloadMode", "audio")
                .put("audioFormat", "best")
                .put("youtubeBetterAudio", true)
                .put("alwaysProxy", true)
                .put("localProcessing", "disabled")
                .toString()

            connection.outputStream.use { output ->
                output.write(
                    body.toByteArray(Charsets.UTF_8)
                )
            }

            val statusCode =
                connection.responseCode

            val responseText = (
                if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                )
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (statusCode !in 200..299) {
                throw IllegalStateException(
                    "Cobalt HTTP $statusCode"
                )
            }

            val response =
                JSONObject(responseText)

            when (
                response.optString("status")
            ) {
                "tunnel",
                "redirect" ->
                    response
                        .optString("url")
                        .trim()
                        .takeIf(::isHttpUrl)
                        ?: throw IllegalStateException(
                            "Cobalt returned no downloadable audio URL."
                        )

                "error" -> {
                    val code = response
                        .optJSONObject("error")
                        ?.optString("code")
                        .orEmpty()

                    throw IllegalStateException(
                        code
                            .takeIf(String::isNotBlank)
                            ?.let {
                                "Cobalt: $it"
                            }
                            ?: "Cobalt could not process this source."
                    )
                }

                else ->
                    throw IllegalStateException(
                        "Cobalt returned an unsupported response."
                    )
            }
        } catch (
            cancelled: CancellationException
        ) {
            throw cancelled
        } finally {
            connection.disconnect()
        }
    }

    private fun isHttpUrl(
        value: String
    ): Boolean =
        value.startsWith(
            "https://",
            ignoreCase = true
        ) || value.startsWith(
            "http://",
            ignoreCase = true
        )
}
