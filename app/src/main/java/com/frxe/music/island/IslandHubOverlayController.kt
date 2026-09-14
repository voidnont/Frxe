package com.frxe.music.island

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.Player
import com.frxe.music.MainActivity
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max

class IslandHubOverlayController(
    private val context: Context,
    private val player: Player
) {
    private var dialog: Dialog? = null
    private var expanded = false
    private var rootView: LinearLayout? = null
    private var topBand: LinearLayout? = null
    private var leftCameraSide: FrameLayout? = null
    private var cameraSpacer: View? = null
    private var rightCameraSide: FrameLayout? = null
    private var titleView: TextView? = null
    private var artistView: TextView? = null
    private var artworkDot: View? = null
    private var waveView: IslandWaveView? = null
    private var progressView: ProgressBar? = null
    private var queueView: TextView? = null
    private var expandedPlayButton: ImageButton? = null
    private var dismissedMediaId: String? = null
    private var widthAnimator: ValueAnimator? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            if (dialog == null) return
            updateDynamicContent()
            mainHandler.postDelayed(this, 500L)
        }
    }

    fun refresh() {
        val state = IslandHubPreferences.state(context)
        val currentMediaId = player.currentMediaItem?.mediaId

        if (currentMediaId == null) {
            dismissedMediaId = null
        }
        if (
            currentMediaId != null &&
            dismissedMediaId != null &&
            dismissedMediaId != currentMediaId
        ) {
            dismissedMediaId = null
        }

        val hasTrack = currentMediaId != null
        if (
            FrxeAppVisibility.isForeground ||
            !state.floatingEnabled ||
            !state.overlayPermissionGranted ||
            !hasTrack ||
            dismissedMediaId == currentMediaId
        ) {
            dismiss()
            return
        }

        if (dialog == null) {
            show()
        }
        updateContent()
    }

    fun dismiss() {
        widthAnimator?.cancel()
        widthAnimator = null
        mainHandler.removeCallbacks(progressTicker)
        dialog?.dismiss()
        dialog = null
        rootView = null
        topBand = null
        leftCameraSide = null
        cameraSpacer = null
        rightCameraSide = null
        titleView = null
        artistView = null
        artworkDot = null
        waveView = null
        progressView = null
        queueView = null
        expandedPlayButton = null
        expanded = false
    }

    private fun show() {
        if (!Settings.canDrawOverlays(context)) return

        val candidate = Dialog(
            context,
            android.R.style.Theme_Translucent_NoTitleBar
        )
        val window = candidate.window ?: return

        window.setType(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        )
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )
        window.setDimAmount(0f)
        window.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        candidate.setCancelable(false)
        candidate.setCanceledOnTouchOutside(false)
        candidate.setContentView(createContent())
        candidate.show()

        dialog = candidate

        window.attributes = window.attributes.apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
            width = dp(COLLAPSED_WIDTH_DP)
            height = WindowManager.LayoutParams.WRAP_CONTENT
            alpha = 1f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        resizeWindow(
            widthDp = COLLAPSED_WIDTH_DP,
            animate = false
        )
        updateContent()
        mainHandler.removeCallbacks(progressTicker)
        mainHandler.post(progressTicker)
    }

    private fun createContent(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumHeight = dp(COMPACT_MIN_HEIGHT_DP)
            background = islandDrawable(expanded = false)
            setOnClickListener {
                if (!expanded) {
                    openFrxe()
                }
            }
            setOnLongClickListener {
                setExpanded(!expanded)
                true
            }
        }
        rootView = root

        val band = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(COMPACT_MIN_HEIGHT_DP)
        }
        topBand = band

        val left = FrameLayout(context)
        leftCameraSide = left
        artworkDot = View(context).apply {
            background = dotDrawable(Color.WHITE)
        }
        left.addView(
            artworkDot,
            FrameLayout.LayoutParams(
                dp(27),
                dp(27),
                Gravity.CENTER
            )
        )

        val center = View(context)
        cameraSpacer = center

        val right = FrameLayout(context)
        rightCameraSide = right
        waveView = IslandWaveView(context)
        right.addView(
            waveView,
            FrameLayout.LayoutParams(
                dp(28),
                dp(18),
                Gravity.CENTER
            )
        )

        band.addView(left)
        band.addView(center)
        band.addView(right)
        root.addView(
            band,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(COMPACT_MIN_HEIGHT_DP)
            )
        )

        return root
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        rebuildExpandedArea()
        resizeWindow(
            widthDp = if (expanded) {
                EXPANDED_WIDTH_DP
            } else {
                COLLAPSED_WIDTH_DP
            },
            animate = true
        )
    }

    private fun rebuildExpandedArea() {
        val root = rootView ?: return
        while (root.childCount > 1) {
            root.removeViewAt(root.childCount - 1)
        }

        root.background = islandDrawable(expanded)

        if (!expanded) {
            titleView = null
            artistView = null
            progressView = null
            queueView = null
            expandedPlayButton = null
            return
        }

        val details = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(5), dp(14), 0)
        }

        val largeArtwork = View(context).apply {
            background = artworkDot?.background?.constantState
                ?.newDrawable()
                ?: dotDrawable(Color.WHITE)
        }
        details.addView(
            largeArtwork,
            LinearLayout.LayoutParams(
                dp(48),
                dp(48)
            ).apply {
                marginEnd = dp(11)
            }
        )

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(
                typeface,
                android.graphics.Typeface.BOLD
            )
        }
        artistView = TextView(context).apply {
            setTextColor(
                Color.argb(166, 255, 255, 255)
            )
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        labels.addView(
            titleView,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        labels.addView(
            artistView,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        details.addView(
            labels,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
        root.addView(
            details,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        progressView = ProgressBar(
            context,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 1000
            progress = 0
            progressTintList =
                ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList =
                ColorStateList.valueOf(
                    Color.argb(42, 255, 255, 255)
                )
        }
        root.addView(
            progressView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3)
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
                topMargin = dp(12)
            }
        )

        queueView = TextView(context).apply {
            setTextColor(
                Color.argb(145, 255, 255, 255)
            )
            textSize = 10.5f
            gravity = Gravity.START
        }
        root.addView(
            queueView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
                topMargin = dp(7)
            }
        )

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }

        val previousButton = mediaButton(
            android.R.drawable.ic_media_previous
        ) {
            PlaybackQueueStore.previous(
                queueRepeatMode()
            )
        }
        expandedPlayButton = mediaButton(
            if (player.isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
        ) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            updateDynamicContent()
        }
        val nextButton = mediaButton(
            android.R.drawable.ic_media_next
        ) {
            PlaybackQueueStore.advance(
                repeatMode = queueRepeatMode(),
                shuffle = player.shuffleModeEnabled
            )
        }

        controls.addView(
            previousButton,
            LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        )
        controls.addView(
            expandedPlayButton,
            LinearLayout.LayoutParams(
                dp(54),
                dp(48)
            ).apply {
                marginStart = dp(17)
                marginEnd = dp(17)
            }
        )
        controls.addView(
            nextButton,
            LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        )
        root.addView(
            controls,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val openButton = TextView(context).apply {
            text = "Open Now Playing"
            setTextColor(
                Color.argb(188, 255, 255, 255)
            )
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(
                dp(12),
                dp(7),
                dp(12),
                dp(12)
            )
            setOnClickListener {
                openFrxe()
            }
        }
        root.addView(
            openButton,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        updateContent()
    }

    private fun updateContent() {
        val item = player.currentMediaItem ?: return

        val seed = item.mediaId.hashCode().absoluteValue
        val r = 72 + seed % 118
        val g = 72 + (seed / 7) % 118
        val b = 72 + (seed / 13) % 118
        artworkDot?.background =
            dotDrawable(Color.rgb(r, g, b))

        titleView?.text =
            item.mediaMetadata.title
                ?.toString()
                ?.ifBlank { "Frxe" }
                ?: "Frxe"
        artistView?.text =
            item.mediaMetadata.artist
                ?.toString()
                ?.ifBlank { "Playing" }
                ?: "Playing"

        updateDynamicContent()
    }

    private fun updateDynamicContent() {
        val duration = player.duration
        val position = player.currentPosition

        progressView?.progress = if (duration > 0L) {
            (
                position.coerceIn(0L, duration) * 1000L /
                    duration
            ).toInt()
        } else {
            0
        }

        val queue = PlaybackQueueStore.state.value
        queueView?.text = if (
            queue.currentIndex >= 0 &&
            queue.entries.isNotEmpty()
        ) {
            "Queue · ${queue.currentIndex + 1} of ${queue.entries.size}"
        } else {
            "Queue"
        }

        expandedPlayButton?.setImageResource(
            if (player.isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
        )

        waveView?.invalidate()
    }

    private fun resizeWindow(
        widthDp: Int,
        animate: Boolean
    ) {
        val window = dialog?.window ?: return
        val displayWidth = displayWidthPx()
        val requested = dp(widthDp)
        val target = IslandLayoutPolicy.clampExpandedWidth(
            displayWidthPx = displayWidth,
            requestedWidthPx = requested,
            edgeMarginPx = dp(12)
        )

        val applyWidth: (Int) -> Unit = { widthPx ->
            window.attributes = window.attributes.apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = 0
                width = widthPx
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            applyCameraSafeTopBand(widthPx)
        }

        widthAnimator?.cancel()

        val start = window.attributes.width
            .takeIf { it > 0 }
            ?: target

        if (!animate || start == target) {
            applyWidth(target)
            return
        }

        widthAnimator = ValueAnimator.ofInt(
            start,
            target
        ).apply {
            duration = 260L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { animator ->
                applyWidth(
                    animator.animatedValue as Int
                )
            }
            start()
        }
    }

    private fun applyCameraSafeTopBand(
        windowWidthPx: Int
    ) {
        val band = topBand ?: return
        val left = leftCameraSide ?: return
        val center = cameraSpacer ?: return
        val right = rightCameraSide ?: return

        val displayWidth = displayWidthPx()
        val cutout = centeredCameraCutout(displayWidth)
        val safe = IslandLayoutPolicy.cameraSafeZone(
            displayWidthPx = displayWidth,
            windowWidthPx = windowWidthPx,
            cutoutLeftPx = cutout?.left,
            cutoutRightPx = cutout?.right,
            fallbackDiameterPx = dp(18),
            paddingPx = dp(10)
        )

        val leftWidth = safe.leftPx
        val centerWidth = safe.widthPx
        val rightWidth =
            (windowWidthPx - safe.rightPx)
                .coerceAtLeast(0)

        left.layoutParams = LinearLayout.LayoutParams(
            leftWidth,
            dp(COMPACT_MIN_HEIGHT_DP)
        )
        center.layoutParams = LinearLayout.LayoutParams(
            centerWidth,
            dp(COMPACT_MIN_HEIGHT_DP)
        )
        right.layoutParams = LinearLayout.LayoutParams(
            rightWidth,
            dp(COMPACT_MIN_HEIGHT_DP)
        )

        val cameraBottom = cutout?.bottom ?: 0
        val requiredHeight = max(
            dp(COMPACT_MIN_HEIGHT_DP),
            cameraBottom + dp(5)
        )
        band.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            requiredHeight
        )
        band.minimumHeight = requiredHeight
    }

    private fun centeredCameraCutout(
        displayWidth: Int
    ): Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null
        }

        val manager =
            context.getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        val rects = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            manager.currentWindowMetrics
                .windowInsets
                .displayCutout
                ?.boundingRects
                .orEmpty()
        } else {
            dialog?.window
                ?.decorView
                ?.rootWindowInsets
                ?.displayCutout
                ?.boundingRects
                .orEmpty()
        }

        return rects
            .filter { rect ->
                rect.centerY() <= dp(72)
            }
            .minByOrNull { rect ->
                abs(rect.centerX() - displayWidth / 2)
            }
    }

    private fun displayWidthPx(): Int {
        val manager =
            context.getSystemService(
                Context.WINDOW_SERVICE
            ) as WindowManager

        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            manager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            android.util.DisplayMetrics().also { metrics ->
                manager.defaultDisplay.getRealMetrics(metrics)
            }.widthPixels
        }
    }

    private fun queueRepeatMode(): QueueRepeatMode =
        when (player.repeatMode) {
            Player.REPEAT_MODE_ALL ->
                QueueRepeatMode.All

            Player.REPEAT_MODE_ONE ->
                QueueRepeatMode.One

            else ->
                QueueRepeatMode.Off
        }

    private fun mediaButton(
        icon: Int,
        action: () -> Unit
    ): ImageButton = ImageButton(context).apply {
        setImageResource(icon)
        setColorFilter(Color.WHITE)
        background = circleDrawable(
            Color.argb(38, 255, 255, 255)
        )
        setPadding(
            dp(10),
            dp(10),
            dp(10),
            dp(10)
        )
        setOnClickListener {
            action()
        }
        contentDescription = "Frxe playback control"
    }

    private fun openFrxe() {
        val intent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        runCatching {
            context.startActivity(intent)
        }
    }

    private fun islandDrawable(
        expanded: Boolean
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.BLACK)
        cornerRadius = dp(
            if (expanded) 34 else 24
        ).toFloat()
    }

    private fun dotDrawable(
        color: Int
    ) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(
            dp(1),
            Color.argb(105, 255, 255, 255)
        )
    }

    private fun circleDrawable(
        color: Int
    ) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dp(value: Int): Int =
        (
            value *
                context.resources.displayMetrics.density
        ).toInt()

    private inner class IslandWaveView(
        context: Context
    ) : View(context) {
        private val paint = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val barWidth = width / 9f
            val gap = barWidth
            val phase =
                (System.currentTimeMillis() / 140L)
                    .toInt()

            repeat(4) { index ->
                val activity = if (player.isPlaying) {
                    ((phase + index * 2) % 5) / 4f
                } else {
                    0.15f
                }
                val minHeight = height * 0.22f
                val barHeight =
                    minHeight +
                        (height * 0.66f * activity)
                val left = gap + index * (barWidth + gap)
                val top = (height - barHeight) / 2f
                val radius = barWidth / 2f
                paint.alpha = if (player.isPlaying) {
                    225
                } else {
                    125
                }
                canvas.drawRoundRect(
                    left,
                    top,
                    left + barWidth,
                    top + barHeight,
                    radius,
                    radius,
                    paint
                )
            }
        }
    }

    companion object {
        private const val COLLAPSED_WIDTH_DP = 174
        private const val EXPANDED_WIDTH_DP = 344
        private const val COMPACT_MIN_HEIGHT_DP = 40
    }
}
