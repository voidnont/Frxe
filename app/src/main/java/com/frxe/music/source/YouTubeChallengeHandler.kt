package com.frxe.music.source

const val YOUTUBE_WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0 Mobile Safari/537.36"

enum class YouTubeChallengeKind {
    VerificationRequired,
    LoginRequired,
    RateLimited
}

data class YouTubeChallenge(
    val kind: YouTubeChallengeKind,
    val message: String
)

object YouTubeChallengeHandler {

    fun classify(error: Throwable): YouTubeChallenge? {
        val combined = buildString {
            append(error::class.java.simpleName)
            append(' ')
            append(error.message.orEmpty())

            var cause = error.cause
            var depth = 0

            while (cause != null && depth < 4) {
                append(' ')
                append(cause::class.java.simpleName)
                append(' ')
                append(cause.message.orEmpty())
                cause = cause.cause
                depth += 1
            }
        }

        return classifyMessage(combined)
    }

    fun classifyMessage(raw: String?): YouTubeChallenge? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        val normalized = value.lowercase()

        return when {
            normalized.contains("http 429") ||
                normalized.contains("status code 429") ||
                normalized.contains("too many requests") ||
                normalized.contains("rate limit") ||
                normalized.contains("ratelimit") ->
                YouTubeChallenge(
                    kind = YouTubeChallengeKind.RateLimited,
                    message = "YouTube is rate limiting requests from this connection."
                )

            normalized.contains("sign in to confirm you're not a bot") ||
                normalized.contains("sign in to confirm you’re not a bot") ||
                normalized.contains("confirm you're not a bot") ||
                normalized.contains("confirm you’re not a bot") ||
                normalized.contains("recaptcha challenge") ||
                normalized.contains("captcha challenge") ||
                normalized.contains("recaptcha required") ||
                normalized.contains("captcha required") ->
                YouTubeChallenge(
                    kind = YouTubeChallengeKind.VerificationRequired,
                    message = "YouTube requires verification before this track can be resolved."
                )

            normalized.contains("login_required") ||
                normalized.contains("login required") ||
                normalized.contains("sign in required") ->
                YouTubeChallenge(
                    kind = YouTubeChallengeKind.LoginRequired,
                    message = "YouTube requires a signed-in session for this track."
                )

            else -> null
        }
    }
}
