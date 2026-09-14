package com.frxe.music.playback

object PlaybackRecoveryPolicy {
    fun shouldReresolve(
        badHttpStatus: Boolean,
        originalSource: String?
    ): Boolean =
        badHttpStatus &&
            originalSource
                ?.trim()
                ?.startsWith(
                    "frxe-catalog://",
                    ignoreCase = true
                ) == true
}

object PlaybackPersistencePolicy {
    fun uriForStorage(
        currentUri: String,
        originalUri: String?
    ): String =
        originalUri
            ?.trim()
            ?.takeIf {
                it.startsWith(
                    "frxe-catalog://",
                    ignoreCase = true
                )
            }
            ?: currentUri

    fun sourceForRestore(
        mediaId: String,
        storedUri: String
    ): String {
        val normalized = storedUri.trim()

        if (
            normalized.startsWith(
                "frxe-catalog://",
                ignoreCase = true
            )
        ) {
            return normalized
        }

        if (mediaId.startsWith("yt-")) {
            val videoId = mediaId.removePrefix("yt-")
            if (videoId.length == 11) {
                return "frxe-catalog://youtube/$videoId"
            }
        }

        return normalized
    }
}

object ResolvedStreamRequestHeaders {
    private const val MAX_ENTRIES = 64

    private val headersByUrl =
        LinkedHashMap<String, Map<String, String>>(
            MAX_ENTRIES,
            0.75f,
            true
        )

    @Synchronized
    fun put(
        url: String,
        headers: Map<String, String>?
    ) {
        val key = url.trim()
        if (key.isEmpty()) return

        val cleaned = headers
            .orEmpty()
            .mapNotNull { (name, value) ->
                val cleanName = name.trim()
                val cleanValue = value.trim()

                if (
                    cleanName.isEmpty() ||
                    cleanValue.isEmpty()
                ) {
                    null
                } else {
                    cleanName to cleanValue
                }
            }
            .toMap()

        if (cleaned.isEmpty()) {
            headersByUrl.remove(key)
            return
        }

        headersByUrl[key] = cleaned

        while (headersByUrl.size > MAX_ENTRIES) {
            val eldest = headersByUrl.entries.firstOrNull()
                ?: break
            headersByUrl.remove(eldest.key)
        }
    }

    @Synchronized
    fun forUrl(url: String?): Map<String, String> =
        url
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(headersByUrl::get)
            .orEmpty()

    @Synchronized
    fun clear() {
        headersByUrl.clear()
    }
}
