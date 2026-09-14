package com.frxe.music.island

data class IslandCameraSafeZone(
    val leftPx: Int,
    val rightPx: Int
) {
    val widthPx: Int
        get() = (rightPx - leftPx).coerceAtLeast(0)
}

object IslandLayoutPolicy {
    fun centeredWindowX(
        displayWidthPx: Int,
        windowWidthPx: Int
    ): Int = ((displayWidthPx - windowWidthPx) / 2).coerceAtLeast(0)

    fun cameraSafeZone(
        displayWidthPx: Int,
        windowWidthPx: Int,
        cutoutLeftPx: Int?,
        cutoutRightPx: Int?,
        fallbackDiameterPx: Int,
        paddingPx: Int
    ): IslandCameraSafeZone {
        val windowLeft = centeredWindowX(displayWidthPx, windowWidthPx)
        val fallbackLeft = (displayWidthPx - fallbackDiameterPx) / 2
        val rawLeft = cutoutLeftPx ?: fallbackLeft
        val rawRight = cutoutRightPx ?: (fallbackLeft + fallbackDiameterPx)

        val localLeft = (rawLeft - windowLeft - paddingPx)
            .coerceIn(0, windowWidthPx)
        val localRight = (rawRight - windowLeft + paddingPx)
            .coerceIn(localLeft, windowWidthPx)

        return IslandCameraSafeZone(
            leftPx = localLeft,
            rightPx = localRight
        )
    }

    fun clampExpandedWidth(
        displayWidthPx: Int,
        requestedWidthPx: Int,
        edgeMarginPx: Int
    ): Int = requestedWidthPx.coerceAtMost(
        (displayWidthPx - edgeMarginPx * 2)
            .coerceAtLeast(0)
    )
}
