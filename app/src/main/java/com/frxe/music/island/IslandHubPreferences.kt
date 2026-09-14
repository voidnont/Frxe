package com.frxe.music.island

import android.content.Context
import android.provider.Settings

data class IslandHubUiState(
    val inAppEnabled: Boolean = true,
    val floatingEnabled: Boolean = false,
    val overlayPermissionGranted: Boolean = false
)

object IslandHubPreferences {
    const val PREFS = "frxe_island_hub"
    const val KEY_IN_APP = "in_app_enabled"
    const val KEY_FLOATING = "floating_enabled"
    const val KEY_REFRESH = "overlay_refresh"

    fun state(context: Context): IslandHubUiState {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return IslandHubUiState(
            inAppEnabled = prefs.getBoolean(KEY_IN_APP, true),
            floatingEnabled = prefs.getBoolean(KEY_FLOATING, false),
            overlayPermissionGranted = Settings.canDrawOverlays(app)
        )
    }

    fun setInAppEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IN_APP, enabled).apply()
    }

    fun refreshFloatingOverlay(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_REFRESH, System.currentTimeMillis()).apply()
    }

    fun setFloatingEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FLOATING, enabled).apply()
    }
}
