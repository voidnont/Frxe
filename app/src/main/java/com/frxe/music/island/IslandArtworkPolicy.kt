package com.frxe.music.island

object IslandArtworkPolicy {
    fun normalize(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("http://", ignoreCase = true) -> value
            else -> null
        }
    }
}
