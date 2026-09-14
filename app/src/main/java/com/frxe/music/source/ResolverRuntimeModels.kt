package com.frxe.music.source

class ResolvedAudioCandidate(
    val url: String,
    headers: Map<String, String> = emptyMap()
) {
    val headers: Map<String, String> = headers
        .mapNotNull { (name, value) ->
            val cleanName = name.trim()
            val cleanValue = value.trim()
            if (cleanName.isEmpty() || cleanValue.isEmpty()) {
                null
            } else {
                cleanName to cleanValue
            }
        }
        .toMap()
}

data class ResolverFailureSummary(
    val resolver: PlaybackResolverKind,
    val message: String,
    val timestampMs: Long
)

data class ResolverDiagnosticsState(
    val lastSuccessfulResolver: PlaybackResolverKind? = null,
    val lastSuccessfulAtMs: Long = 0L,
    val recentFailures: List<ResolverFailureSummary> = emptyList(),
    val ytDlpInitializationError: String? = null
)

object ResolverDiagnosticsPolicy {
    private val urlPattern = Regex(
        "https?://\\S+",
        RegexOption.IGNORE_CASE
    )
    private val secretPattern = Regex(
        "(?i)(authorization|cookie|set-cookie|x-goog-[^: ]+|sig|signature|token)\\s*[:=]\\s*[^ ]+"
    )

    fun sanitizeMessage(
        raw: String?,
        maxLength: Int = 180
    ): String {
        val normalized = raw
            .orEmpty()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(urlPattern, "<url>")
            .replace(secretPattern, "<redacted>")
            .replace(Regex("\\s+"), " ")
            .trim()

        return normalized
            .take(maxLength)
            .ifBlank { "Resolver failed" }
    }

    fun appendFailure(
        current: List<ResolverFailureSummary>,
        failure: ResolverFailureSummary,
        maxEntries: Int = 6
    ): List<ResolverFailureSummary> =
        (
            current + failure.copy(
                message = sanitizeMessage(
                    failure.message
                )
            )
        ).takeLast(
            maxEntries.coerceAtLeast(1)
        )
}
