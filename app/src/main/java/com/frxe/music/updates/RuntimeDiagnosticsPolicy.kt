package com.frxe.music.updates

object RuntimeDiagnosticsPolicy {
    private val urlPattern =
        Regex(
            "https?://\\S+",
            RegexOption.IGNORE_CASE
        )

    private val sensitivePattern =
        Regex(
            "(?i)\\b(sig|signature|token|key|auth|authorization|cookie)=([^\\s&]+)"
        )

    private val whitespacePattern =
        Regex("\\s+")

    fun sanitize(
        message: String?
    ): String {
        val normalized =
            message
                .orEmpty()
                .replace(
                    urlPattern,
                    "[url]"
                )
                .replace(
                    sensitivePattern
                ) { match ->
                    "${match.groupValues[1]}=[redacted]"
                }
                .replace(
                    whitespacePattern,
                    " "
                )
                .trim()
                .ifBlank {
                    "Unknown runtime error"
                }

        return normalized.take(180)
    }

    fun appendBounded(
        existing: List<String>,
        message: String,
        maxEntries: Int = 8
    ): List<String> {
        if (maxEntries <= 0) {
            return emptyList()
        }

        return (
            existing +
                sanitize(message)
            )
            .takeLast(maxEntries)
    }
}
