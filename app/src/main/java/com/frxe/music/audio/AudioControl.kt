package com.frxe.music.audio

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Opens the device's audio-effect control panel when one is available.
 * Frxe keeps this hook provider-neutral so compatible system audio panels work.
 */
object AudioControl {
    fun openEqualizer(context: Context) {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
