package com.frxe.music.source

internal object ZexlPlaybackContract {
    fun normalizeBaseUrl(value: String): String =
        value.trim().trimEnd('/')

    fun isConfigured(value: String): Boolean {
        val baseUrl = normalizeBaseUrl(value)

        return baseUrl.startsWith(
            "https://",
            ignoreCase = true
        ) || baseUrl.startsWith(
            "http://",
            ignoreCase = true
        )
    }

    fun fileUrl(
        baseUrl: String,
        downloadUrl: String?,
        jobId: String
    ): String {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        val candidate = downloadUrl
            ?.trim()
            .orEmpty()

        return when {
            candidate.startsWith(
                "https://",
                ignoreCase = true
            ) || candidate.startsWith(
                "http://",
                ignoreCase = true
            ) -> candidate

            candidate.startsWith('/') ->
                normalizedBase + candidate

            candidate.isNotEmpty() ->
                "$normalizedBase/$candidate"

            else ->
                "$normalizedBase/api/jobs/$jobId/file"
        }
    }

    fun authorizationHeaders(
        apiKey: String?
    ): Map<String, String> =
        apiKey
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let {
                mapOf(
                    "Authorization" to "Bearer $it"
                )
            }
            ?: emptyMap()
}
