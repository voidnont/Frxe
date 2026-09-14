package com.frxe.music.updates

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RuntimeHealthStore {
    private const val PREFS_NAME =
        "frxe_runtime_health"

    private const val FAILURE_SEPARATOR =
        "\u001F"

    private val lock = Any()

    private var prefs:
        SharedPreferences? = null

    private val _state =
        MutableStateFlow(
            RuntimeHealthState()
        )

    val state:
        StateFlow<RuntimeHealthState> =
        _state.asStateFlow()

    fun initialize(
        context: Context
    ) {
        synchronized(lock) {
            if (prefs != null) {
                return
            }

            val loadedPrefs =
                context.applicationContext
                    .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )

            prefs = loadedPrefs
            _state.value =
                RuntimeHealthState(
                    ytDlpInitialized =
                        loadedPrefs.getBoolean(
                            "yt_dlp_initialized",
                            false
                        ),
                    ytDlpVersion =
                        loadedPrefs.getString(
                            "yt_dlp_version",
                            null
                        ),
                    ytDlpInitStatus =
                        loadedPrefs.getString(
                            "yt_dlp_init_status",
                            "Not initialized"
                        ) ?: "Not initialized",
                    lastYtDlpUpdateAttemptMs =
                        loadedPrefs.getLong(
                            "yt_dlp_last_update_attempt_ms",
                            0L
                        ),
                    ytDlpUpdateStatus =
                        loadedPrefs.getString(
                            "yt_dlp_update_status",
                            "Not checked"
                        ) ?: "Not checked",
                    lastSuccessfulResolver =
                        loadedPrefs.getString(
                            "last_successful_resolver",
                            null
                        ),
                    lastResolverSuccessMs =
                        loadedPrefs.getLong(
                            "last_resolver_success_ms",
                            0L
                        ),
                    lastResolverFailureMs =
                        loadedPrefs.getLong(
                            "last_resolver_failure_ms",
                            0L
                        ),
                    recentResolverFailures =
                        loadedPrefs
                            .getString(
                                "recent_resolver_failures",
                                ""
                            )
                            .orEmpty()
                            .split(FAILURE_SEPARATOR)
                            .filter(String::isNotBlank)
                            .takeLast(8)
                )
        }
    }

    fun recordResolverSuccess(
        resolver: String,
        nowMs: Long =
            System.currentTimeMillis()
    ) {
        mutate { current ->
            RuntimeHealthReducer
                .resolverSuccess(
                    current,
                    resolver,
                    nowMs
                )
        }
    }

    fun recordResolverFailure(
        resolver: String,
        message: String?,
        nowMs: Long =
            System.currentTimeMillis()
    ) {
        mutate { current ->
            RuntimeHealthReducer
                .resolverFailure(
                    current,
                    resolver,
                    message,
                    nowMs
                )
        }
    }

    fun recordYtDlpInit(
        version: String?,
        error: String?
    ) {
        mutate { current ->
            RuntimeHealthReducer.ytDlpInit(
                current,
                version,
                error
            )
        }
    }

    fun recordYtDlpUpdateAttempt(
        nowMs: Long
    ) {
        mutate { current ->
            RuntimeHealthReducer
                .ytDlpUpdateAttempt(
                    current,
                    nowMs
                )
        }
    }

    fun recordYtDlpUpdateResult(
        version: String?,
        status: String?,
        error: String?
    ) {
        mutate { current ->
            RuntimeHealthReducer
                .ytDlpUpdateResult(
                    current,
                    version,
                    status,
                    error
                )
        }
    }

    private inline fun mutate(
        transform: (RuntimeHealthState) ->
            RuntimeHealthState
    ) {
        synchronized(lock) {
            val next =
                transform(_state.value)

            _state.value = next
            persist(next)
        }
    }

    private fun persist(
        state: RuntimeHealthState
    ) {
        prefs
            ?.edit()
            ?.putBoolean(
                "yt_dlp_initialized",
                state.ytDlpInitialized
            )
            ?.putString(
                "yt_dlp_version",
                state.ytDlpVersion
            )
            ?.putString(
                "yt_dlp_init_status",
                state.ytDlpInitStatus
            )
            ?.putLong(
                "yt_dlp_last_update_attempt_ms",
                state.lastYtDlpUpdateAttemptMs
            )
            ?.putString(
                "yt_dlp_update_status",
                state.ytDlpUpdateStatus
            )
            ?.putString(
                "last_successful_resolver",
                state.lastSuccessfulResolver
            )
            ?.putLong(
                "last_resolver_success_ms",
                state.lastResolverSuccessMs
            )
            ?.putLong(
                "last_resolver_failure_ms",
                state.lastResolverFailureMs
            )
            ?.putString(
                "recent_resolver_failures",
                state.recentResolverFailures
                    .joinToString(
                        FAILURE_SEPARATOR
                    )
            )
            ?.apply()
    }
}
