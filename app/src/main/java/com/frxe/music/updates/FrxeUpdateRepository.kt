package com.frxe.music.updates

import android.os.Build
import com.frxe.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val version: String,
        val pageUrl: String,
        val notes: String
    ) : UpdateCheckResult

    data class UpToDate(val latestVersion: String) : UpdateCheckResult
    data object NoPublishedRelease : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

class FrxeUpdateRepository {
    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Frxe/${BuildConfig.VERSION_NAME} Android/${Build.VERSION.SDK_INT}")
            }
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@runCatching UpdateCheckResult.NoPublishedRelease
                }
                if (connection.responseCode !in 200..299) {
                    return@runCatching UpdateCheckResult.Error("GitHub returned ${connection.responseCode}")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val release = parseGitHubReleasePayload(body)
                if (release.version.isBlank()) {
                    return@runCatching UpdateCheckResult.Error("Latest release has no version tag")
                }
                if (compareVersions(release.version, currentVersion) > 0) {
                    UpdateCheckResult.UpdateAvailable(
                        version = release.version,
                        pageUrl = release.pageUrl,
                        notes = release.notes
                    )
                } else {
                    UpdateCheckResult.UpToDate(release.version)
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { UpdateCheckResult.Error(it.message ?: "Update check failed") }
    }

    companion object {
        const val LATEST_RELEASE_API = "https://api.github.com/repos/voidnont/Frxe/releases/latest"
        const val RELEASES_PAGE = "https://github.com/voidnont/Frxe/releases"
    }
}
