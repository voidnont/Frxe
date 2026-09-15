package com.frxe.music.source

class CatalogSearchCache<T>(
    private val ttlMs: Long,
    private val maxEntries: Int = 32
) {
    private data class Entry<T>(
        val value: T,
        val storedAtMs: Long
    )

    private val entries =
        LinkedHashMap<String, Entry<T>>(
            maxEntries,
            0.75f,
            true
        )

    @Synchronized
    fun get(
        query: String,
        nowMs: Long = System.currentTimeMillis()
    ): T? {
        val key = normalize(query)
        val entry = entries[key] ?: return null
        if (nowMs - entry.storedAtMs > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(
        query: String,
        value: T,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = normalize(query)
        if (key.isEmpty()) return
        entries[key] = Entry(value, nowMs)
        while (entries.size > maxEntries) {
            val eldest = entries.entries.firstOrNull()?.key ?: break
            entries.remove(eldest)
        }
    }

    private fun normalize(query: String): String =
        query.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
}
