package com.frxe.music.island

import kotlin.math.abs

object IslandSwipePolicy {
    fun shouldDismiss(horizontalOffsetPx: Float, thresholdPx: Float): Boolean {
        if (thresholdPx <= 0f) return horizontalOffsetPx != 0f
        return abs(horizontalOffsetPx) >= thresholdPx
    }
}
