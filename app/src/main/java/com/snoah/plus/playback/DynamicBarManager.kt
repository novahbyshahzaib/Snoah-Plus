package com.snoah.plus.playback

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.snoah.plus.MainActivity
import com.snoah.plus.R
import com.snoah.plus.data.LibraryStore
import com.snoah.plus.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin

/**
 * Snoah Plus's own floating "dynamic bar": a small black pill that
 * floats at the top of the screen over other apps while music plays,
 * and expands into a card with the song, progress and controls when
 * tapped. This is drawn by Snoah Plus itself with a system overlay
 * window, because the phone's built-in dynamic bar is not guaranteed
 * to pick up every app. The look stays Snoah Plus monochrome: black
 * pill/card, white text and icons.
 *
 * The bar only shows while a song is loaded, the feature is switched
 * on in Settings, the "display over other apps" permission is
 * granted, and Snoah Plus itself is not in the foreground (when the
 * app is open, its own mini-player and Now Playing screen are the
 * controls). Tapping the card body opens Snoah Plus.
 */
object DynamicBarManager {

    private var appContext: Context? = null
    private var windowManager: WindowManager? = null
    private var library: LibraryStore? = null
    private var started = false
    private var appInForeground = false
    private var overlayAdded = false
    private var expanded = false

    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var pillView: View? = null
    private var cardView: View? = null
    private var cardArtwork: ImageView? = null
    private var cardTitle: TextView? = null
    private var cardArtist: TextView? = null
    private var cardProgress: ProgressBar? = null
    private var cardTimeNow: TextView? = null
    private var cardTimeTotal: TextView? = null
    private var cardPlayPause: ImageButton? = null
    private var cardBars: EqualizerBarsView? = null
    private var pillArtwork: ImageView? = null
    private var pillBars: EqualizerBarsView? = null

    private var currentSongId: Long = -1L
    private var currentArtworkKey: String? = null
    private var isPlaying = false
    private var positionMs = 0L
    private var durationMs = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    fun start(context: Context) {
        if (started) {
            refresh()
            return
        }
        started = true
        appContext = context.applicationContext
        library = LibraryStore(context.applicationContext)
        windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        observeJob = scope.launch {
            combine(PlayerManager.state, PlayerManager.positionMs) { state, position ->
                Triple(state, position, Unit)
            }.collect { (state, position, _) ->
                val songId = state.currentSong?.id ?: -1L
                val songChanged = songId != currentSongId
                isPlaying = state.isPlaying
                positionMs = position
                durationMs = state.durationMs
                if (songChanged) {
                    currentSongId = songId
                    currentArtworkKey = null
                    // New song (or queue cleared): re-evaluate whether
                    // the bar should be shown at all.
                    refresh()
                }
                updateCardContents(state.currentSong?.title, state.currentSong?.artist)
            }
        }
        refresh()
    }

    fun stop() {
        started = false
        observeJob?.cancel()
        observeJob = null
        removeOverlay()
    }

    fun setAppInForeground(inForeground: Boolean) {
        if (appInForeground == inForeground) return
        appInForeground = inForeground
        refresh()
    }

    fun refresh() {
        if (!started) return
        val ctx = appContext ?: return
        val enabled = try { library?.getDynamicBarEnabled() ?: true } catch (e: Exception) { true }
        val allowed = try { Settings.canDrawOverlays(ctx) } catch (e: Exception) { false }
        val show = enabled && allowed && !appInForeground && currentSongId >= 0
        if (!show) {
            removeOverlay()
            return
        }
        if (!overlayAdded) {
            addOverlay(ctx)
        } else {
            applyExpandedState()
        }
        val song = PlayerManager.state.value.currentSong
        updateCardContents(song?.title, song?.artist)
        loadArtwork(ctx, song?.albumArtUri?.toString(), song?.id)
    }

    private fun dp(value: Int): Int {
        val ctx = appContext ?: return value
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()
    }

    private fun statusBarHeight(): Int {
        val ctx = appContext ?: return 0
        val resId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) ctx.resources.getDimensionPixelSize(resId) else dp(24)
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun addOverlay(ctx: Context) {
        try {
            val wm = windowManager ?: return
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }
            val pill = buildPill(ctx)
            val card = buildCard(ctx)
            root.addView(pill)
            root.addView(card)
            pillView = pill
            cardView = card
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(8)
            }
            params = layoutParams
            rootView = root
            wm.addView(root, layoutParams)
            overlayAdded = true
            applyExpandedState()
        } catch (e: Exception) {
            overlayAdded = false
        }
    }

    private fun removeOverlay() {
        if (!overlayAdded) return
        try {
            rootView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
        }
        overlayAdded = false
        expanded = false
        rootView = null
        pillView = null
        cardView = null
        cardArtwork = null
        pillArtwork = null
    }

    private fun applyExpandedState() {
        val wm = windowManager ?: return
        val root = rootView ?: return
        val layoutParams = params ?: return
        pillView?.visibility = if (expanded) View.GONE else View.VISIBLE
        cardView?.visibility = if (expanded) View.VISIBLE else View.GONE
        layoutParams.width = if (expanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }
        layoutParams.y = if (expanded) statusBarHeight() + dp(10) else dp(8)
        try {
            wm.updateViewLayout(root, layoutParams)
        } catch (e: Exception) {
        }
    }

    private fun buildPill(ctx: Context): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Color.parseColor("#111111"), 999)
            setPadding(dp(8), dp(6), dp(12), dp(6))
            setOnClickListener {
                expanded = true
                applyExpandedState()
            }
        }
        val art = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        pillArtwork = art
        row.addView(art)
        val bars = EqualizerBarsView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(16)).apply {
                leftMargin = dp(10)
            }
        }
        pillBars = bars
        row.addView(bars)
        return row
    }

    private fun buildCard(ctx: Context): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.parseColor("#111111"), 24)
            setPadding(dp(16), dp(16), dp(16), dp(14))
        }
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dp(16)
            rightMargin = dp(16)
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { openApp(ctx) }
        }
        val art = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        cardArtwork = art
        topRow.addView(art)
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(12)
            }
        }
        val title = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        cardTitle = title
        texts.addView(title)
        val artist = TextView(ctx).apply {
            setTextColor(Color.parseColor("#BDBDBD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        cardArtist = artist
        texts.addView(artist)
        topRow.addView(texts)
        val bars = EqualizerBarsView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(16)).apply {
                leftMargin = dp(8)
            }
        }
        cardBars = bars
        topRow.addView(bars)
        card.addView(topRow)

        val times = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val now = TextView(ctx).apply {
            setTextColor(Color.parseColor("#BDBDBD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = "0:00"
        }
        cardTimeNow = now
        times.addView(now)
        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(4), 1f).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                gravity = Gravity.CENTER_VERTICAL
            }
            max = 1000
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#3A3A3A"))
        }
        cardProgress = progress
        times.addView(progress)
        val total = TextView(ctx).apply {
            setTextColor(Color.parseColor("#BDBDBD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = "0:00"
        }
        cardTimeTotal = total
        times.addView(total)
        card.addView(times)

        val controls = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val prev = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.WHITE)
            background = null
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { PlayerManager.previous() }
        }
        controls.addView(prev)
        val playPause = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.BLACK)
            background = roundedBackground(Color.WHITE, 999)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                leftMargin = dp(20)
                rightMargin = dp(20)
            }
            setOnClickListener { PlayerManager.playPause() }
        }
        cardPlayPause = playPause
        controls.addView(playPause)
        val next = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(Color.WHITE)
            background = null
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { PlayerManager.next() }
        }
        controls.addView(next)
        val collapse = TextView(ctx).apply {
            text = "⌄"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                leftMargin = dp(16)
            }
            gravity = Gravity.CENTER
            setOnClickListener {
                expanded = false
                applyExpandedState()
            }
        }
        controls.addView(collapse)
        card.addView(controls)
        card.visibility = View.GONE
        return card
    }

    private fun openApp(ctx: Context) {
        try {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
        }
        expanded = false
        applyExpandedState()
    }

    private fun updateCardContents(title: String?, artist: String?) {
        cardTitle?.text = title ?: ""
        cardArtist?.text = artist ?: ""
        cardPlayPause?.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        cardBars?.setPlaying(isPlaying)
        pillBars?.setPlaying(isPlaying)
        val total = durationMs
        cardTimeNow?.text = FormatUtils.formatDuration(positionMs)
        cardTimeTotal?.text = FormatUtils.formatDuration(total)
        if (total > 0) {
            cardProgress?.progress = ((positionMs.toFloat() / total.toFloat()) * 1000f).toInt().coerceIn(0, 1000)
        } else {
            cardProgress?.progress = 0
        }
    }

    private fun loadArtwork(ctx: Context, uriString: String?, songId: Long?) {
        if (songId == null || songId < 0) return
        val key = uriString ?: ""
        if (key == currentArtworkKey) return
        currentArtworkKey = key
        pillArtwork?.setImageResource(R.mipmap.ic_launcher)
        cardArtwork?.setImageResource(R.mipmap.ic_launcher)
        if (key.isBlank()) return
        val wantedId = songId
        scope.launch(Dispatchers.IO) {
            val bitmap = try {
                val uri = android.net.Uri.parse(key)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                val sample = run {
                    var s = 1
                    while (options.outWidth / (s * 2) >= 96 && options.outHeight / (s * 2) >= 96) s *= 2
                    s
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (currentSongId != wantedId) return@withContext
                if (bitmap != null) {
                    pillArtwork?.setImageBitmap(bitmap)
                    cardArtwork?.setImageBitmap(bitmap)
                }
            }
        }
    }

    /** Small animated equaliser bars, white on black. */
    private class EqualizerBarsView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val handler = Handler(Looper.getMainLooper())
        private var phase = 0f
        private var playing = false
        private var ticking = false

        private val ticker = object : Runnable {
            override fun run() {
                ticking = false
                if (!playing || !isAttachedToWindow) return
                phase += 0.45f
                invalidate()
                handler.postDelayed(this, 160)
                ticking = true
            }
        }

        fun setPlaying(value: Boolean) {
            if (playing == value) return
            playing = value
            if (value) {
                if (!ticking) {
                    handler.post(ticker)
                    ticking = true
                }
            } else {
                handler.removeCallbacks(ticker)
                ticking = false
                invalidate()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (playing && !ticking) {
                handler.post(ticker)
                ticking = true
            }
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(ticker)
            ticking = false
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val barCount = 4
            val gap = width.toFloat() / (barCount * 2f)
            val barWidth = gap
            val radius = barWidth / 2f
            for (i in 0 until barCount) {
                val fraction = if (playing) {
                    0.3f + 0.7f * abs(sin(phase + i * 1.1f))
                } else {
                    0.3f
                }
                val barHeight = height * fraction
                val left = gap * (i * 2f) + gap / 2f
                val top = height - barHeight
                canvas.drawRoundRect(left, top, left + barWidth, height.toFloat(), radius, radius, paint)
            }
        }
    }
}
