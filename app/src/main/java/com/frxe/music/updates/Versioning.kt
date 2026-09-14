package com.frxe.music.updates

private data class VersionParts(
    val numbers: List<Int>,
    val prerelease: String?
)

private fun parseVersion(raw: String): VersionParts {
    val normalized = raw.trim().removePrefix("v").removePrefix("V")
    val main = normalized.substringBefore('-')
    val prerelease = normalized.substringAfter('-', missingDelimiterValue = "")
        .ifBlank { null }
    return VersionParts(
        numbers = main.split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 },
        prerelease = prerelease
    )
}

fun compareVersions(left: String, right: String): Int {
    val a = parseVersion(left)
    val b = parseVersion(right)
    val count = maxOf(a.numbers.size, b.numbers.size)
    repeat(count) { index ->
        val av = a.numbers.getOrElse(index) { 0 }
        val bv = b.numbers.getOrElse(index) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return when {
        a.prerelease == null && b.prerelease != null -> 1
        a.prerelease != null && b.prerelease == null -> -1
        else -> (a.prerelease ?: "").compareTo(b.prerelease ?: "")
    }
}
