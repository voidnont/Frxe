package com.frxe.music.source

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

internal data class PlaybackResolverHedgePolicy(
    val ytDlpHeadStartMs: Long = 750L,
    val localBudgetMs: Long = 8_000L
)

class PlaybackResolverChain(
    private val resolvers: List<
        Pair<
            PlaybackResolverKind,
            suspend () -> ResolvedAudioCandidate?
        >
    >,
    private val hedgePolicy: PlaybackResolverHedgePolicy =
        PlaybackResolverHedgePolicy()
) {
    suspend fun resolve(): PlaybackResolutionResult {
        if (
            resolvers.size > 1 &&
            resolvers.first().first == PlaybackResolverKind.YtDlp
        ) {
            return resolveYtDlpFirstHedged()
        }

        return resolveSequentially()
    }

    private suspend fun resolveYtDlpFirstHedged(): PlaybackResolutionResult {
        val result = CompletableDeferred<PlaybackResolutionResult>()
        val primaryFinished = CompletableDeferred<Unit>()
        val remaining = AtomicInteger(resolvers.size)
        val attempts = arrayOfNulls<PlaybackResolverAttempt>(resolvers.size)
        val attemptsLock = Any()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

        fun snapshotAttempts(): List<PlaybackResolverAttempt> =
            synchronized(attemptsLock) {
                attempts.filterNotNull()
            }

        resolvers.forEachIndexed { index, (kind, resolver) ->
            scope.launch {
                try {
                    if (index > 0) {
                        withTimeoutOrNull(
                            hedgePolicy.ytDlpHeadStartMs
                        ) {
                            primaryFinished.await()
                        }

                        if (result.isCompleted) {
                            return@launch
                        }
                    }

                    val outcome = runAttempt(kind, resolver)

                    synchronized(attemptsLock) {
                        attempts[index] = outcome.attempt
                    }

                    if (
                        outcome.candidate != null &&
                        outcome.normalizedUrl != null
                    ) {
                        result.complete(
                            PlaybackResolutionResult.Success(
                                stream = PlaybackResolvedStream(
                                    url = outcome.normalizedUrl,
                                    resolver = kind,
                                    headers = outcome.candidate.headers
                                ),
                                attempts = snapshotAttempts()
                            )
                        )
                    } else if (
                        remaining.decrementAndGet() == 0
                    ) {
                        result.complete(
                            terminalResult(snapshotAttempts())
                        )
                    }
                } finally {
                    if (index == 0) {
                        primaryFinished.complete(Unit)
                    }
                }
            }
        }

        return try {
            result.await()
        } finally {
            scope.cancel()
        }
    }

    private suspend fun resolveSequentially(): PlaybackResolutionResult {
        val attempts = mutableListOf<PlaybackResolverAttempt>()

        for ((kind, resolver) in resolvers) {
            val outcome = runAttempt(kind, resolver)
            attempts += outcome.attempt

            if (
                outcome.candidate != null &&
                outcome.normalizedUrl != null
            ) {
                return PlaybackResolutionResult.Success(
                    stream = PlaybackResolvedStream(
                        url = outcome.normalizedUrl,
                        resolver = kind,
                        headers = outcome.candidate.headers
                    ),
                    attempts = attempts.toList()
                )
            }
        }

        return terminalResult(attempts)
    }

    private suspend fun runAttempt(
        kind: PlaybackResolverKind,
        resolver: suspend () -> ResolvedAudioCandidate?
    ): ResolverOutcome = try {
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

        ResolverOutcome(
            candidate = candidate,
            normalizedUrl = normalizedUrl,
            attempt = PlaybackResolverAttempt(
                resolver = kind,
                errorMessage = if (normalizedUrl == null) {
                    "No playable audio URL returned."
                } else {
                    null
                }
            )
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        val challenge = YouTubeChallengeHandler
            .classify(error)

        ResolverOutcome(
            candidate = null,
            normalizedUrl = null,
            attempt = PlaybackResolverAttempt(
                resolver = kind,
                errorMessage = error.message
                    ?.take(180),
                challenge = challenge
            )
        )
    }

    private fun terminalResult(
        attempts: List<PlaybackResolverAttempt>
    ): PlaybackResolutionResult {
        val challenge = attempts
            .firstNotNullOfOrNull { attempt ->
                attempt.challenge
            }

        if (challenge != null) {
            return PlaybackResolutionResult
                .VerificationRequired(
                    challenge = challenge,
                    attempts = attempts
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
            attempts = attempts
        )
    }

    private data class ResolverOutcome(
        val candidate: ResolvedAudioCandidate?,
        val normalizedUrl: String?,
        val attempt: PlaybackResolverAttempt
    )
}
