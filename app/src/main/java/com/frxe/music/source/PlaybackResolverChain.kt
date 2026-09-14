package com.frxe.music.source

enum class PlaybackResolverKind {
    NewPipe,
    InnerTube,
    YtDlp
}

data class PlaybackResolvedStream(
    val url: String,
    val resolver: PlaybackResolverKind,
    val headers: Map<String, String> = emptyMap()
)

data class PlaybackResolverAttempt(
    val resolver: PlaybackResolverKind,
    val errorMessage: String? = null,
    val challenge: YouTubeChallenge? = null
)

sealed interface PlaybackResolutionResult {
    data class Success(
        val stream: PlaybackResolvedStream,
        val attempts: List<PlaybackResolverAttempt>
    ) : PlaybackResolutionResult

    data class VerificationRequired(
        val challenge: YouTubeChallenge,
        val attempts: List<PlaybackResolverAttempt>
    ) : PlaybackResolutionResult

    data class Failed(
        val message: String,
        val attempts: List<PlaybackResolverAttempt>
    ) : PlaybackResolutionResult
}

class PlaybackResolverChain(
    private val resolvers: List<
        Pair<
            PlaybackResolverKind,
            suspend () -> ResolvedAudioCandidate?
        >
    >
) {
    suspend fun resolve(): PlaybackResolutionResult {
        val attempts = mutableListOf<PlaybackResolverAttempt>()
        var firstChallenge: YouTubeChallenge? = null

        for ((kind, resolver) in resolvers) {
            try {
                val candidate = resolver()
                val normalizedUrl = candidate
                    ?.url
                    ?.trim()
                    ?.takeIf {
                        it.startsWith(
                            "https://",
                            ignoreCase = true
                        ) || it.startsWith(
                            "http://",
                            ignoreCase = true
                        )
                    }

                attempts += PlaybackResolverAttempt(
                    resolver = kind,
                    errorMessage = if (normalizedUrl == null) {
                        "No playable audio URL returned."
                    } else {
                        null
                    }
                )

                if (
                    candidate != null &&
                    normalizedUrl != null
                ) {
                    return PlaybackResolutionResult.Success(
                        stream = PlaybackResolvedStream(
                            url = normalizedUrl,
                            resolver = kind,
                            headers = candidate.headers
                        ),
                        attempts = attempts.toList()
                    )
                }
            } catch (error: Throwable) {
                val challenge = YouTubeChallengeHandler
                    .classify(error)

                if (
                    firstChallenge == null &&
                    challenge != null
                ) {
                    firstChallenge = challenge
                }

                attempts += PlaybackResolverAttempt(
                    resolver = kind,
                    errorMessage = error.message
                        ?.take(180),
                    challenge = challenge
                )
            }
        }

        val challenge = firstChallenge

        if (challenge != null) {
            return PlaybackResolutionResult
                .VerificationRequired(
                    challenge = challenge,
                    attempts = attempts.toList()
                )
        }

        return PlaybackResolutionResult.Failed(
            message = attempts
                .asReversed()
                .firstNotNullOfOrNull { attempt ->
                    attempt.errorMessage
                        ?.takeIf(String::isNotBlank)
                }
                ?: "No resolver returned a playable audio stream.",
            attempts = attempts.toList()
        )
    }
}
