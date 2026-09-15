package com.frxe.music.ui.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity

private class PlayerGestureScrollBoundary {
    var downwardBoundarySeen: Boolean = false
}

@Composable
fun Modifier.playerGestures(
    enabled: Boolean,
    isTv: Boolean,
    blocked: Boolean,
    onAction: (PlayerGestureAction) -> Unit
): Modifier {
    val density = LocalDensity.current
    val scrollBoundary = remember {
        PlayerGestureScrollBoundary()
    }
    val nestedScrollConnection = remember(scrollBoundary) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0f) {
                    scrollBoundary.downwardBoundarySeen = true
                }
                return Offset.Zero
            }
        }
    }

    if (!enabled || isTv || blocked) {
        return this
    }

    return nestedScroll(nestedScrollConnection)
        .pointerInput(enabled, isTv, blocked, density) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                scrollBoundary.downwardBoundarySeen = false
                var totalX = 0f
                var totalY = 0f
                var childConsumed = false
                var pressed = true

                while (pressed) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: break

                    childConsumed = childConsumed || change.isConsumed

                    val delta = change.positionChange()
                    totalX += delta.x
                    totalY += delta.y
                    pressed = change.pressed
                }

                val action = PlayerGesturePolicy.action(
                    deltaXDp = with(density) { totalX.toDp().value },
                    deltaYDp = with(density) { totalY.toDp().value },
                    enabled = enabled,
                    isTv = isTv,
                    blocked = blocked,
                    childConsumed = childConsumed,
                    canCollapse =
                        !childConsumed || scrollBoundary.downwardBoundarySeen
                )

                if (action != PlayerGestureAction.None) {
                    onAction(action)
                }
            }
        }
}
