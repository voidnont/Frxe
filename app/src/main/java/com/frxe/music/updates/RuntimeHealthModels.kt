package com.frxe.music.updates

data class RuntimeHealthState(
    val ytDlpInitialized: Boolean = false,
    val ytDlpVersion: String? = null,
    val ytDlpInitStatus: String = "Not initialized",
    val lastYtDlpUpdateAttemptMs: Long = 0L,
    val ytDlpUpdateStatus: String = "Not checked",
    val lastSuccessfulResolver: String? = null,
    val lastResolverSuccessMs: Long = 0L,
    val lastResolverFailureMs: Long = 0L,
    val recentResolverFailures: List<String> = emptyList()
)

object RuntimeHealthReducer {
    fun resolverSuccess(
        state: RuntimeHealthState,
        resolver: String,
        nowMs: Long
    ): RuntimeHealthState =
        state.copy(
            lastSuccessfulResolver = resolver,
            lastResolverSuccessMs = nowMs
        )

    fun resolverFailure(
        state: RuntimeHealthState,
        resolver: String,
        message: String?,
        nowMs: Long
    ): RuntimeHealthState {
        val safe =
            "$resolver: ${RuntimeDiagnosticsPolicy.sanitize(message)}"

        return state.copy(
            lastResolverFailureMs = nowMs,
            recentResolverFailures =
                RuntimeDiagnosticsPolicy.appendBounded(
                    state.recentResolverFailures,
                    safe
                )
        )
    }

    fun ytDlpInit(
        state: RuntimeHealthState,
        version: String?,
        error: String?
    ): RuntimeHealthState =
        if (error == null) {
            state.copy(
                ytDlpInitialized = true,
                ytDlpVersion = version,
                ytDlpInitStatus = "Ready"
            )
        } else {
            state.copy(
                ytDlpInitialized = false,
                ytDlpInitStatus =
                    "Initialization failed: " +
                    RuntimeDiagnosticsPolicy.sanitize(error)
            )
        }

    fun ytDlpUpdateAttempt(
        state: RuntimeHealthState,
        nowMs: Long
    ): RuntimeHealthState =
        state.copy(
            lastYtDlpUpdateAttemptMs = nowMs,
            ytDlpUpdateStatus =
                "Checking stable channel…"
        )

    fun ytDlpUpdateResult(
        state: RuntimeHealthState,
        version: String?,
        status: String?,
        error: String?
    ): RuntimeHealthState =
        if (error == null) {
            state.copy(
                ytDlpInitialized = true,
                ytDlpVersion =
                    version ?: state.ytDlpVersion,
                ytDlpUpdateStatus =
                    status
                        ?.takeIf(String::isNotBlank)
                        ?: "Already current"
            )
        } else {
            state.copy(
                ytDlpUpdateStatus =
                    "Update failed: " +
                    RuntimeDiagnosticsPolicy.sanitize(error)
            )
        }
}
