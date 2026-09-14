package com.frxe.music.updates

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class DependencySpec(
    val name: String,
    val currentVersion: String,
    val metadataUrl: String,
    val updateMode: DependencyUpdateMode =
        DependencyUpdatePolicy
            .compiledDependencyMode()
)

data class DependencyReleaseStatus(
    val name: String,
    val currentVersion: String,
    val latestVersion: String?,
    val updateAvailable: Boolean,
    val updateMode: DependencyUpdateMode,
    val error: String? = null
)

class DependencyReleaseRepository {
    suspend fun checkAll():
        List<DependencyReleaseStatus> =
        coroutineScope {
            DEPENDENCIES
                .map { spec ->
                    async {
                        check(spec)
                    }
                }
                .awaitAll()
        }

    private suspend fun check(
        spec: DependencySpec
    ): DependencyReleaseStatus =
        withContext(Dispatchers.IO) {
            checkBlocking(spec)
        }

    private fun checkBlocking(
        spec: DependencySpec
    ): DependencyReleaseStatus {
        return try {
            val connection =
                (
                    URL(
                        spec.metadataUrl
                    ).openConnection() as
                        HttpURLConnection
                    )
                    .apply {
                        requestMethod = "GET"
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        setRequestProperty(
                            "User-Agent",
                            "Frxe dependency release checker"
                        )
                    }

            try {
                if (
                    connection.responseCode !in
                        200..299
                ) {
                    return DependencyReleaseStatus(
                        name = spec.name,
                        currentVersion =
                            spec.currentVersion,
                        latestVersion = null,
                        updateAvailable = false,
                        updateMode =
                            spec.updateMode,
                        error =
                            "HTTP ${connection.responseCode}"
                    )
                }

                val xml =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                val latest =
                    parseMavenMetadata(
                        xml
                    )

                DependencyReleaseStatus(
                    name = spec.name,
                    currentVersion =
                        spec.currentVersion,
                    latestVersion = latest,
                    updateAvailable =
                        latest != null &&
                            compareVersions(
                                latest,
                                spec.currentVersion
                            ) > 0,
                    updateMode =
                        spec.updateMode,
                    error =
                        if (latest == null) {
                            "No version in metadata"
                        } else {
                            null
                        }
                )
            } finally {
                connection.disconnect()
            }
        } catch (error: Throwable) {
            DependencyReleaseStatus(
                name = spec.name,
                currentVersion =
                    spec.currentVersion,
                latestVersion = null,
                updateAvailable = false,
                updateMode =
                    spec.updateMode,
                error =
                    error.message
                        ?: "Check failed"
            )
        }
    }

    companion object {
        val DEPENDENCIES =
            listOf(
                DependencySpec(
                    "Android Gradle Plugin",
                    "9.4.0",
                    "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"
                ),
                DependencySpec(
                    "Kotlin Compose plugin",
                    "2.4.20",
                    "https://plugins.gradle.org/m2/org/jetbrains/kotlin/plugin/compose/org.jetbrains.kotlin.plugin.compose.gradle.plugin/maven-metadata.xml"
                ),
                DependencySpec(
                    "KSP",
                    "2.3.12",
                    "https://repo1.maven.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml"
                ),
                DependencySpec(
                    "Activity",
                    "1.13.0",
                    "https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml"
                ),
                DependencySpec(
                    "Lifecycle",
                    "2.11.0",
                    "https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/maven-metadata.xml"
                ),
                DependencySpec(
                    "Compose",
                    "1.12.1",
                    "https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui/maven-metadata.xml"
                ),
                DependencySpec(
                    "Material 3",
                    "1.4.0",
                    "https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/maven-metadata.xml"
                ),
                DependencySpec(
                    "Media3",
                    "1.11.0",
                    "https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/maven-metadata.xml"
                ),
                DependencySpec(
                    "Cast",
                    "22.3.1",
                    "https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-cast-framework/maven-metadata.xml"
                ),
                DependencySpec(
                    "Room",
                    "2.8.5",
                    "https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/maven-metadata.xml"
                ),
                DependencySpec(
                    "DataStore",
                    "1.2.1",
                    "https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences/maven-metadata.xml"
                ),
                DependencySpec(
                    "OkHttp",
                    "5.5.0",
                    "https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml"
                ),
                DependencySpec(
                    "Backdrop",
                    "2.0.1",
                    "https://repo1.maven.org/maven2/io/github/kyant0/backdrop/maven-metadata.xml"
                ),
                DependencySpec(
                    "VOSK",
                    "0.3.75",
                    "https://repo1.maven.org/maven2/com/alphacephei/vosk-android/maven-metadata.xml"
                ),
                DependencySpec(
                    "FFmpegKit",
                    "8.1.7",
                    "https://repo1.maven.org/maven2/dev/ffmpegkit-maintained/ffmpeg-kit-full/maven-metadata.xml"
                ),
                DependencySpec(
                    "yt-dlp Android wrapper",
                    "0.18.1",
                    "https://repo1.maven.org/maven2/io/github/junkfood02/youtubedl-android/library/maven-metadata.xml"
                ),
                DependencySpec(
                    "Desugar JDK NIO",
                    "2.1.5",
                    "https://dl.google.com/dl/android/maven2/com/android/tools/desugar_jdk_libs_nio/maven-metadata.xml"
                ),
                DependencySpec(
                    "NewPipeExtractor",
                    "v0.26.4",
                    "https://jitpack.io/com/github/TeamNewPipe/NewPipeExtractor/maven-metadata.xml"
                )
            )
    }
}
