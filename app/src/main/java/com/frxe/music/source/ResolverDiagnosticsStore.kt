package com.frxe.music.source

import android.content.Context
import com.frxe.music.updates.RuntimeHealthStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ResolverDiagnosticsStore {
    private const val PREFS = "frxe_resolver_diagnostics"
    private const val KEY_LAST_RESOLVER = "last_resolver"
    private const val KEY_LAST_SUCCESS_AT = "last_success_at"
    private const val KEY_YTDLP_INIT_ERROR = "ytdlp_init_error"

    private val lock = Any()
    private var appContext: Context? = null

    private val _state = MutableStateFlow(
        ResolverDiagnosticsState()
    )
    val state: StateFlow<ResolverDiagnosticsState> =
        _state.asStateFlow()

    fun initialize(context: Context) {
        synchronized(lock) {
            if (appContext != null) return
            appContext = context.applicationContext
            val prefs = appContext!!
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
            val lastResolver = prefs
                .getString(KEY_LAST_RESOLVER, null)
                ?.let { raw ->
                    runCatching {
                        PlaybackResolverKind.valueOf(raw)
                    }.getOrNull()
                }
            _state.value = _state.value.copy(
                lastSuccessfulResolver = lastResolver,
                lastSuccessfulAtMs = prefs.getLong(
                    KEY_LAST_SUCCESS_AT,
                    0L
                ),
                ytDlpInitializationError = prefs
                    .getString(KEY_YTDLP_INIT_ERROR, null)
            )
        }
    }

    fun recordSuccess(
        resolver: PlaybackResolverKind,
        nowMs: Long = System.currentTimeMillis()
    ) {
        _state.value = _state.value.copy(
            lastSuccessfulResolver = resolver,
            lastSuccessfulAtMs = nowMs
        )
        persistCoreState()

        RuntimeHealthStore.recordResolverSuccess(
            resolver = resolver.displayName,
            nowMs = nowMs
        )
    }

    fun recordFailure(
        resolver: PlaybackResolverKind,
        error: Throwable?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        recordFailure(
            resolver = resolver,
            message = error?.message,
            nowMs = nowMs
        )
    }

    fun recordFailure(
        resolver: PlaybackResolverKind,
        message: String?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val failure = ResolverFailureSummary(
            resolver = resolver,
            message = ResolverDiagnosticsPolicy
                .sanitizeMessage(message),
            timestampMs = nowMs
        )
        _state.value = _state.value.copy(
            recentFailures = ResolverDiagnosticsPolicy
                .appendFailure(
                    _state.value.recentFailures,
                    failure
                )
        )

        RuntimeHealthStore.recordResolverFailure(
            resolver = resolver.displayName,
            message = message,
            nowMs = nowMs
        )
    }

    fun recordYtDlpInitializationSuccess() {
        _state.value = _state.value.copy(
            ytDlpInitializationError = null
        )
        persistCoreState()
    }

    fun recordYtDlpInitializationFailure(
        error: Throwable
    ) {
        _state.value = _state.value.copy(
            ytDlpInitializationError =
                ResolverDiagnosticsPolicy
                    .sanitizeMessage(error.message)
        )
        persistCoreState()
    }

    private fun persistCoreState() {
        val context = appContext ?: return
        val snapshot = _state.value
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_LAST_RESOLVER,
                snapshot.lastSuccessfulResolver?.name
            )
            .putLong(
                KEY_LAST_SUCCESS_AT,
                snapshot.lastSuccessfulAtMs
            )
            .putString(
                KEY_YTDLP_INIT_ERROR,
                snapshot.ytDlpInitializationError
            )
            .apply()
    }

    private val PlaybackResolverKind.displayName: String
        get() = when (this) {
            PlaybackResolverKind.NewPipe -> "NewPipe"
            PlaybackResolverKind.InnerTube -> "InnerTube"
            PlaybackResolverKind.YtDlp -> "yt-dlp"
        }
}
