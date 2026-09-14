package com.frxe.music.updates

data class UpdaterUiState(
    val checkingApp: Boolean = false,
    val appStatus: String = "Not checked",
    val latestAppVersion: String? = null,
    val releasePageUrl: String? = null,
    val checkingDependencies: Boolean = false,
    val dependencies: List<DependencyReleaseStatus> = emptyList(),
    val updatingYtDlp: Boolean = false,
    val ytDlpStatus: String = "Not checked"
)
