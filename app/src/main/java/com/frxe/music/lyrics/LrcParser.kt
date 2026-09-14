package com.frxe.music.lyrics

data class ParsedLyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

private val timestampRegex = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

fun parseSyncedLyrics(raw: String, durationMs: Long): List<ParsedLyricLine> {
    val starts = buildList {
        raw.lineSequence().forEach lineLoop@ { rawLine ->
            val matches = timestampRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@lineLoop
            val text = rawLine.replace(timestampRegex, "").trim()
            if (text.isBlank()) return@lineLoop
            matches.forEach matchLoop@ { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@matchLoop
                val seconds = match.groupValues[2].toLongOrNull() ?: return@matchLoop
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                add((minutes * 60_000L) + (seconds * 1_000L) + millis to text)
            }
        }
    }.sortedBy { it.first }

    if (starts.isEmpty()) return emptyList()
    val safeDuration = durationMs.coerceAtLeast(starts.last().first)
    return starts.mapIndexed { index, (startMs, text) ->
        val nextStart = starts.getOrNull(index + 1)?.first
        val endMs = if (nextStart != null) (nextStart - 1L).coerceAtLeast(startMs) else safeDuration
        ParsedLyricLine(startMs, endMs, text)
    }
}
