package com.frxe.music.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackResolutionStage {
    Idle,
    Resolving,
    Resolved,
    Ready,
    VerificationRequired,
    Failed
}

data class PlaybackResolutionStatus(
    val trackId: String? = null,
    val stage: PlaybackResolutionStage = PlaybackResolutionStage.Idle,
    val message: String? = null,
    val resolver: PlaybackResolverKind? = null
) {
    val canRetry: Boolean
        get() = stage == PlaybackResolutionStage.Resolved ||
            stage == PlaybackResolutionStage.VerificationRequired ||
            stage == PlaybackResolutionStage.Failed
}

object PlaybackResolutionMonitor {
    private val _state = MutableStateFlow(
        PlaybackResolutionStatus()
    )

    val state = _state.asStateFlow()

    fun resolving(trackId: String) {
        _state.value = PlaybackResolutionStatus(
            trackId = trackId,
            stage = PlaybackResolutionStage.Resolving,
            message = "Trying NewPipe, InnerTube and yt-dlp…"
        )
    }

    fun resolvingFallback(
        trackId: String,
        fallbackName: String
    ) {
        if (!isCurrent(trackId)) return

        _state.value = PlaybackResolutionStatus(
            trackId = trackId,
            stage = PlaybackResolutionStage.Resolving,
            message = "Local audio resolvers failed. Trying $fallbackName backup…"
        )
    }

    fun isCurrent(trackId: String): Boolean =
        _state.value.trackId == trackId

    fun resolved(
        trackId: String,
        resolver: PlaybackResolverKind?
    ) {
        if (!isCurrent(trackId)) return

        _state.value = PlaybackResolutionStatus(
            trackId = trackId,
            stage = PlaybackResolutionStage.Resolved,
            message = resolver?.let {
                "Audio resolved with ${it.label}. Starting player…"
            } ?: "Audio source ready. Starting player…",
            resolver = resolver
        )
    }

    fun resolvedFallback(
        trackId: String,
        fallbackName: String
    ) {
        if (!isCurrent(trackId)) return

        _state.value = PlaybackResolutionStatus(
            trackId = trackId,
            stage = PlaybackResolutionStage.Resolved,
            message = "Audio resolved with $fallbackName backup. Starting player…"
        )
    }

    fun ready(trackId: String?) {
        val actualTrackId = trackId
            ?: return

        val current = _state.value

        if (
            current.trackId != null &&
            current.trackId != actualTrackId
        ) {
            return
        }

        _state.value = current.copy(
            trackId = actualTrackId,
            stage = PlaybackResolutionStage.Ready,
            message = null
        )
    }

    fun verificationRequired(
        trackId: String,
        challenge: YouTubeChallenge
    ) {
        if (!isCurrent(trackId)) return

        _state.value = PlaybackResolutionStatus(
            trackId = trackId,
            stage = PlaybackResolutionStage.VerificationRequired,
            message = challenge.message +
                " Complete any verification YouTube shows normally, then tap Retry."
        )
    }

    fun failed(
        trackId: String?,
        message: String
    ) {
        val current = _state.value

        if (
            trackId != null &&
            current.trackId != null &&
            current.trackId != trackId
        ) {
            return
        }

        _state.value = PlaybackResolutionStatus(
            trackId = trackId ?: current.trackId,
            stage = PlaybackResolutionStage.Failed,
            message = message.ifBlank {
                "Frxe could not start this audio stream."
            },
            resolver = current.resolver
        )
    }
}

private val PlaybackResolverKind.label: String
    get() = when (this) {
        PlaybackResolverKind.NewPipe -> "NewPipe"
        PlaybackResolverKind.InnerTube -> "InnerTube"
        PlaybackResolverKind.YtDlp -> "yt-dlp"
    }
