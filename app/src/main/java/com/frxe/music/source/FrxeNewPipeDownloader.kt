package com.frxe.music.source

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

internal object FrxeNewPipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val body = request.dataToSend()
            ?.toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

        val builder = OkRequest.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .addHeader(
                "User-Agent",
                YOUTUBE_WEB_USER_AGENT
            )

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { value ->
                builder.addHeader(name, value)
            }
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body
                ?.string()
                .orEmpty()

            if (response.code == 429) {
                throw ReCaptchaException(
                    "YouTube rate limited the request",
                    request.url()
                )
            }

            val challenge = YouTubeChallengeHandler
                .classifyMessage(responseBody)

            if (challenge != null) {
                throw ReCaptchaException(
                    challenge.message,
                    request.url()
                )
            }

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString()
            )
        }
    }
}

internal object FrxeNewPipeRuntime {
    private val initLock = Any()

    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return

        synchronized(initLock) {
            if (initialized) return

            org.schabi.newpipe.extractor.NewPipe.init(
                FrxeNewPipeDownloader
            )

            initialized = true
        }
    }
}
