package dev.fluidkey.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import dev.fluidkey.theme.GlassRenderer
import dev.fluidkey.theme.KeyboardTheme
import dev.fluidkey.theme.Themes

/**
 * Transparent layer above the whole input view that draws key previews and long-press
 * popups as glass bubbles. Living above the toolbar lets top-row popups extend past the
 * keyboard's top edge. It never consumes touches.
 */
class PopupOverlay(context: Context) : View(context) {
    var renderer = GlassRenderer(resources.displayMetrics.density)
    var theme: KeyboardTheme = Themes.DARK
        set(v) { field = v; renderer.theme = v }
    var offsetX = 0f
    var offsetY = 0f

    private val dp = resources.displayMetrics.density
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.DEFAULT
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()

    private var previewRect: RectF? = null
    private var previewText = ""
    private var previewStart = 0L

    private var panelRect: RectF? = null
    private var cells: List<RectF> = emptyList()
    private var items: List<String> = emptyList()
    private var selected = 0
    private var panelStart = 0L
    private var pillX = 0f
    private var pillFromX = 0f
    private var pillStart = 0L

    private val ease = DecelerateInterpolator(1.8f)
    private val animate get() = ValueAnimator.areAnimatorsEnabled()

    init {
        isClickable = false
        isFocusable = false
    }

    /**
     * Never ask for height of our own. The IME window measures the input view with
     * AT_MOST(screen height); a plain View would take all of it and stretch the keyboard
     * to full screen. FrameLayout re-measures MATCH_PARENT children with the final
     * EXACTLY height, so we still cover the whole input view.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    private fun now() = AnimationUtils.currentAnimationTimeMillis()

    fun showPreview(r: RectF, label: String) {
        // Sliding between keys keeps the bubble up, so only animate a fresh appearance.
        if (previewRect == null) previewStart = now()
        previewRect = r
        previewText = label
        invalidate()
    }

    fun hidePreview() {
        if (previewRect != null) {
            previewRect = null
            invalidate()
        }
    }

    fun showPanel(panel: RectF, cellRects: List<RectF>, labels: List<String>, sel: Int) {
        panelRect = panel
        cells = cellRects
        items = labels
        selected = sel
        panelStart = now()
        pillX = cellRects.getOrNull(sel)?.left ?: 0f
        pillFromX = pillX
        pillStart = 0L
        previewRect = null
        invalidate()
    }

    fun setSelection(sel: Int) {
        if (sel != selected && sel in cells.indices) {
            pillFromX = currentPillX(now())
            pillX = cells[sel].left
            pillStart = now()
            selected = sel
            invalidate()
        }
    }

    fun hidePanel() {
        if (panelRect != null) {
            panelRect = null
            invalidate()
        }
    }

    fun hideAll() {
        previewRect = null
        panelRect = null
        invalidate()
    }

    private fun progress(start: Long, dur: Long, now: Long): Float =
        if (!animate) 1f else ease.getInterpolation(((now - start).toFloat() / dur).coerceIn(0f, 1f))

    private fun currentPillX(now: Long): Float {
        val t = progress(pillStart, 120, now)
        return pillFromX + (pillX - pillFromX) * t
    }

    private fun drawSoftShadow(canvas: Canvas, r: RectF, radius: Float, alpha: Float) {
        // Layered translucent rounded rects: a cheap, hardware-friendly soft shadow.
        val base = if (theme.isDark) 0x000000 else 0x1C2230
        for (i in 1..4) {
            tmp.set(r)
            tmp.inset(-i * 1.6f * dp, -i * 1.2f * dp)
            tmp.offset(0f, i * 1.1f * dp)
            shadow.color = base or (((if (theme.isDark) 26 else 14) * alpha / i).toInt() shl 24)
            canvas.drawRoundRect(tmp, radius + i * dp, radius + i * dp, shadow)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = now()
        var running = false
        canvas.save()
        canvas.translate(offsetX, offsetY)
        val radius = 14 * dp
        previewRect?.let { r ->
            val t = progress(previewStart, 90, now)
            if (t < 1f) running = true
            val s = 0.85f + 0.15f * t
            canvas.save()
            canvas.scale(s, s, r.centerX(), r.bottom)
            drawSoftShadow(canvas, r, radius, t)
            renderer.drawPopup(canvas, r, radius, (255 * t).toInt())
            text.color = theme.popupText
            text.alpha = (255 * t).toInt()
            text.textSize = r.height() * 0.52f
            canvas.drawText(previewText, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text)
            canvas.restore()
        }
        panelRect?.let { p ->
            val t = progress(panelStart, 110, now)
            if (t < 1f) running = true
            val s = 0.9f + 0.1f * t
            canvas.save()
            canvas.scale(s, s, p.centerX(), p.bottom)
            drawSoftShadow(canvas, p, radius, t)
            renderer.drawPopup(canvas, p, radius, (255 * t).toInt())
            cells.getOrNull(selected)?.let { cell ->
                val x = currentPillX(now)
                if (x != pillX) running = true
                tmp.set(x, cell.top, x + cell.width(), cell.bottom)
                renderer.drawGlass(canvas, tmp, radius * 0.75f, GlassRenderer.Kind.ACCENT)
            }
            for ((i, cell) in cells.withIndex()) {
                text.color = if (i == selected) theme.onAccent else theme.popupText
                text.alpha = (255 * t).toInt()
                text.textSize = cell.height() * 0.46f
                canvas.drawText(items[i], cell.centerX(), cell.centerY() - (text.descent() + text.ascent()) / 2, text)
            }
            canvas.restore()
        }
        canvas.restore()
        if (running) postInvalidateOnAnimation()
        pill.alpha = 255
    }
}
