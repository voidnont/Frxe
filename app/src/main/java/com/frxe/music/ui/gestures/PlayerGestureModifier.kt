package com.frxe.music.ui.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity

@Composable
fun Modifier.playerGestures(
    enabled: Boolean,
    isTv: Boolean,
    blocked: Boolean,
    onAction: (PlayerGestureAction) -> Unit
): Modifier {
    val density = LocalDensity.current

    if (!enabled || isTv || blocked) {
        return this
    }

    return pointerInput(enabled, isTv, blocked, density) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            var totalX = 0f
            var totalY = 0f
            var cancelled = false
            var pressed = true

            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: break

                if (change.isConsumed) {
                    cancelled = true
                    break
                }

                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                pressed = change.pressed
            }

            if (!cancelled) {
                val action = PlayerGesturePolicy.action(
                    deltaXDp = with(density) { totalX.toDp().value },
                    deltaYDp = with(density) { totalY.toDp().value },
                    enabled = enabled,
                    isTv = isTv,
                    blocked = blocked
                )

                if (action != PlayerGestureAction.None) {
                    onAction(action)
                }
            }
        }
    }
}
