package com.frxe.music.updates

data class GitHubReleasePayload(
    val version: String,
    val pageUrl: String,
    val notes: String
)

fun parseGitHubReleasePayload(json: String): GitHubReleasePayload {
    fun field(name: String): String {
        val pattern = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        val encoded = pattern.find(json)?.groupValues?.getOrNull(1).orEmpty()
        return encoded
            .replace("\\\\n", "\n")
            .replace("\\\\r", "\r")
            .replace("\\\\t", "\t")
            .replace("\\\\\"", "\"")
            .replace("\\\\\\\\", "\\")
    }

    return GitHubReleasePayload(
        version = field("tag_name").ifBlank { field("name") },
        pageUrl = field("html_url"),
        notes = field("body")
    )
}

fun parseMavenMetadata(xml: String): String? {
    val release = Regex("<release>\\s*([^<]+?)\\s*</release>", RegexOption.IGNORE_CASE)
        .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (release.isNotEmpty()) return release

    val latest = Regex("<latest>\\s*([^<]+?)\\s*</latest>", RegexOption.IGNORE_CASE)
        .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    if (latest.isNotEmpty()) return latest

    return Regex("<version>\\s*([^<]+?)\\s*</version>", RegexOption.IGNORE_CASE)
        .findAll(xml)
        .map { it.groupValues[1].trim() }
        .filter(String::isNotBlank)
        .lastOrNull()
}
