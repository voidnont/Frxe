package com.frxe.music.island

import kotlin.math.PI
import kotlin.math.sin

object IslandWaveformPolicy {
    fun levels(
        phase: Float,
        isPlaying: Boolean
    ): List<Float> {
        if (!isPlaying) {
            return listOf(0.30f, 0.46f, 0.36f, 0.26f)
        }

        val normalized = phase - kotlin.math.floor(phase)
        val offsets = floatArrayOf(0.00f, 0.19f, 0.43f, 0.67f)
        return offsets.mapIndexed { index, offset ->
            val wave = (
                sin(
                    (normalized + offset) *
                        (2.0 * PI) *
                        (1.0 + index * 0.07)
                ) + 1.0
                ) / 2.0

            (0.18f + (wave.toFloat() * 0.82f))
                .coerceIn(0.18f, 1f)
        }
    }
}
