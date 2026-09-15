package com.frxe.music.ui.gestures

import kotlin.math.abs

enum class PlayerGestureAction {
    None,
    Previous,
    Next,
    Collapse
}

object PlayerGesturePolicy {
    private const val HORIZONTAL_THRESHOLD_DP = 72f
    private const val VERTICAL_THRESHOLD_DP = 88f
    private const val DIRECTION_DOMINANCE = 1.15f

    fun action(
        deltaXDp: Float,
        deltaYDp: Float,
        enabled: Boolean,
        isTv: Boolean,
        blocked: Boolean
    ): PlayerGestureAction {
        if (!enabled || isTv || blocked) {
            return PlayerGestureAction.None
        }

        val absX = abs(deltaXDp)
        val absY = abs(deltaYDp)

        if (
            absX >= HORIZONTAL_THRESHOLD_DP &&
            absX > absY * DIRECTION_DOMINANCE
        ) {
            return if (deltaXDp < 0f) {
                PlayerGestureAction.Next
            } else {
                PlayerGestureAction.Previous
            }
        }

        if (
            deltaYDp >= VERTICAL_THRESHOLD_DP &&
            absY > absX * DIRECTION_DOMINANCE
        ) {
            return PlayerGestureAction.Collapse
        }

        return PlayerGestureAction.None
    }
}
