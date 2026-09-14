package com.frxe.music.save

import android.content.Context
import com.frxe.music.BuildConfig
import com.frxe.music.playback.ResolvedStreamRequestHeaders
import com.frxe.music.source.PlaybackResolutionResult
import com.frxe.music.source.PlaybackResolverKind
import com.frxe.music.source.PlaybackStreamResolver
import kotlinx.coroutines.CancellationException

internal class DownloadPipeline(
    context: Context
) {

    private val local =
        FrxeSaveEngine(context)

    private val resolver =
        DownloadAudioResolver()

    private val zexl =
        ZexlSaveEngine(
            context = context,
            baseUrl = BuildConfig.ZEXL_BASE_URL,
            apiKey = BuildConfig.ZEXL_API_KEY
                .takeIf(String::isNotBlank)
        )

    private val cobalt =
        CobaltSaveEngine(
            context = context,
            baseUrl = BuildConfig.COBALT_BASE_URL,
            apiKey = BuildConfig.COBALT_API_KEY
                .takeIf(String::isNotBlank)
        )

    suspend fun save(
        request: SaveRequest,
        onState: (SaveUiState) -> Unit
    ): SaveResult {
        val sourceUrl =
            PlaybackStreamResolver
                .youtubeWatchUrl(
                    request.sourceUrl
                )
                ?: request.sourceUrl.trim()

        require(sourceUrl.isNotEmpty()) {
            "No download source was provided."
        }

        require(
            DownloadRoutePolicy
                .backendsFor(sourceUrl)
                .isNotEmpty()
        ) {
            "No compatible downloadable source is available for this track."
        }

        var localError: Throwable? = null
        var zexlError: Throwable? = null

        try {
            val localSource = when {
                isDirectDownloadUrl(sourceUrl) -> {
                    ResolvedLocalSource(
                        url = sourceUrl,
                        backend = DownloadBackend.Direct
                    )
                }

                isYouTubePageUrl(sourceUrl) -> {
                    val resolution = resolver.resolve(
                        sourceUrl
                    ) { kind ->
                        val backend = kind.toDownloadBackend()

                        onState(
                            SaveUiState(
                                stage = SaveStage.Validating,
                                progress = 0.02f,
                                message = "Trying ${backend.label}…",
                                backend = backend
                            )
                        )
                    }

                    when (resolution) {
                        is PlaybackResolutionResult.Success -> {
                            ResolvedStreamRequestHeaders.put(
                                url = resolution.stream.url,
                                headers = resolution.stream.headers
                            )

                            ResolvedLocalSource(
                                url = resolution.stream.url,
                                backend = resolution.stream.resolver
                                    .toDownloadBackend()
                            )
                        }

                        is PlaybackResolutionResult.VerificationRequired ->
                            throw IllegalStateException(
                                resolution.challenge.message
                            )

                        is PlaybackResolutionResult.Failed ->
                            throw IllegalStateException(
                                resolution.message
                            )
                    }
                }

                else -> {
                    throw IllegalStateException(
                        "The selected source is not a direct downloadable audio source."
                    )
                }
            }

            onState(
                SaveUiState(
                    stage = SaveStage.Validating,
                    progress = 0.04f,
                    message =
                        "Audio source ready with ${localSource.backend.label}",
                    backend = localSource.backend
                )
            )

            return local.save(
                request.copy(
                    sourceUrl = localSource.url
                )
            ) { state ->
                onState(
                    state.copy(
                        backend = localSource.backend
                    )
                )
            }
        } catch (
            cancelled: CancellationException
        ) {
            throw cancelled
        } catch (
            error: Throwable
        ) {
            localError = error
        }

        if (zexl.configured) {
            onState(
                SaveUiState(
                    stage = SaveStage.Validating,
                    progress = 0.02f,
                    message =
                        "Local resolvers failed · trying Zexl",
                    backend = DownloadBackend.Zexl
                )
            )

            try {
                return zexl.save(
                    request.copy(
                        sourceUrl = sourceUrl
                    ),
                    onState
                )
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (
                error: Throwable
            ) {
                zexlError = error
            }
        }

        if (cobalt.configured) {
            onState(
                SaveUiState(
                    stage = SaveStage.Validating,
                    progress = 0.02f,
                    message =
                        "Zexl unavailable/failed · trying Cobalt",
                    backend = DownloadBackend.Cobalt
                )
            )

            try {
                return cobalt.save(
                    request.copy(
                        sourceUrl = sourceUrl
                    ),
                    onState
                )
            } catch (
                cancelled: CancellationException
            ) {
                throw cancelled
            } catch (
                cobaltError: Throwable
            ) {
                throw IllegalStateException(
                    buildFailureMessage(
                        localError = localError,
                        zexlError = zexlError,
                        cobaltError = cobaltError
                    ),
                    cobaltError
                )
            }
        }

        throw IllegalStateException(
            buildFailureMessage(
                localError = localError,
                zexlError = zexlError,
                cobaltError = null
            )
        )
    }

    fun cancel() {
        local.cancel()
        zexl.cancel()
        cobalt.cancel()
    }

    private fun buildFailureMessage(
        localError: Throwable?,
        zexlError: Throwable?,
        cobaltError: Throwable?
    ): String {
        val parts = mutableListOf<String>()

        localError
            ?.message
            ?.takeIf(String::isNotBlank)
            ?.let {
                parts += "Local: $it"
            }

        zexlError
            ?.message
            ?.takeIf(String::isNotBlank)
            ?.let {
                parts += "Zexl: $it"
            }

        cobaltError
            ?.message
            ?.takeIf(String::isNotBlank)
            ?.let {
                parts += "Cobalt: $it"
            }

        if (!cobalt.configured) {
            parts += "Cobalt is not configured"
        }

        return if (parts.isEmpty()) {
            "Download failed."
        } else {
            "Download failed. " +
                parts.joinToString(" · ")
        }
    }

    private data class ResolvedLocalSource(
        val url: String,
        val backend: DownloadBackend
    )

    private fun DownloadResolverKind.toDownloadBackend():
        DownloadBackend =
        when (this) {
            DownloadResolverKind.InnerTube ->
                DownloadBackend.InnerTube

            DownloadResolverKind.NewPipe ->
                DownloadBackend.NewPipe

            DownloadResolverKind.YtDlp ->
                DownloadBackend.YtDlp
        }

    private fun PlaybackResolverKind.toDownloadBackend():
        DownloadBackend =
        when (this) {
            PlaybackResolverKind.InnerTube ->
                DownloadBackend.InnerTube

            PlaybackResolverKind.NewPipe ->
                DownloadBackend.NewPipe

            PlaybackResolverKind.YtDlp ->
                DownloadBackend.YtDlp
        }
}
