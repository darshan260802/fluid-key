package dev.fluidkey.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.animation.ValueAnimator
import android.os.Build
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import dev.fluidkey.theme.GlassRenderer
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import dev.fluidkey.R
import dev.fluidkey.theme.KeyboardTheme
import dev.fluidkey.theme.Themes
import dev.fluidkey.touch.AdaptiveTouchResolver
import dev.fluidkey.touch.HitKey
import dev.fluidkey.touch.NextCharPredictor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class ShiftState { OFF, ON, LOCKED }

/**
 * Canvas-drawn keyboard with multi-touch rollover, adaptive hit targets, long-press popups,
 * space-bar cursor seek and backspace swipe-to-delete.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val resolver: AdaptiveTouchResolver) : View(context) {

    interface Listener {
        fun onText(text: String, fromLetter: Boolean)
        fun onKeyCode(code: Int)
        fun onCursorMove(delta: Int)
        fun onDeleteSwipe(words: Int)
        fun onDeleteSwipeEnd(words: Int)
        fun onSpaceLongPress()
        fun currentPrefix(): String
        fun feedback(code: Int)
    }

    var listener: Listener? = null
    var overlay: PopupOverlay? = null
    var predictor: NextCharPredictor? = null

    /** Shared glass painter (the service passes its own so panels and keys match). */
    var renderer = GlassRenderer(resources.displayMetrics.density)
        set(v) { field = v; v.theme = theme; staticDirty = true; invalidate() }
    var theme: KeyboardTheme = Themes.DARK
        set(v) {
            field = v
            renderer.theme = v
            staticDirty = true
            invalidate()
        }
    var keyBorders = true
        set(v) { field = v; staticDirty = true; invalidate() }
    var showHints = true
        set(v) { field = v; staticDirty = true; invalidate() }
    var showPreview = true
    var showHitMap = false
    var backspaceSwipe = true
    var longPressDelay = 300L
    var seekStepPx = 24f
    var heightScale = 1f
        set(v) { field = v; requestLayout() }
    var spaceLabel = "FluidKey"

    /** Editor action shown on the enter key, already reduced to "none" when the field wants newlines. */
    var enterAction = EditorInfo.IME_ACTION_NONE
        set(v) { if (field != v) { field = v; staticDirty = true; invalidate() } }

    var shiftState = ShiftState.OFF
        set(v) { if (field != v) { field = v; staticDirty = true; invalidate() } }

    var layout: KeyboardLayout = Layouts.letters(false)
        set(v) {
            cancelAll()
            field = v
            hitMap = null
            staticDirty = true
            requestLayout()
            if (width > 0 && height > 0) layoutKeys(width, height)
            invalidate()
        }

    private val dp = resources.displayMetrics.density
    private val timer = Handler(Looper.getMainLooper())
    private val padTop = 4 * dp
    private val padBottom = 6 * dp
    private val gapH = 5 * dp
    private val gapV = 9 * dp
    private val radius = 10 * dp
    private val baseRowHeight get() = 56 * dp * heightScale

    private class LaidKey(val key: Key, val rect: RectF, val touch: RectF, val hit: HitKey)

    private val laid = ArrayList<LaidKey>()
    private val hitKeys = ArrayList<HitKey>()
    private val pressed = HashSet<LaidKey>()
    private var unit = 1f

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val medium: Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = medium
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = medium
    }
    private val icons = HashMap<Int, Drawable>()

    init {
        renderer.theme = theme
    }

    // Static layer: every key at rest, drawn once into a bitmap (with soft shadows, which need
    // a software canvas). Per frame we only blit it and draw the few keys that are animating.
    private var staticLayer: Bitmap? = null
    private var staticDirty = true

    private class Anim(val from: Float, val to: Float, val start: Long, val dur: Long, val spring: Boolean) {
        fun value(now: Long): Float {
            val t = ((now - start).toFloat() / dur).coerceIn(0f, 1f)
            val e = if (spring) SPRING.getInterpolation(t) else EASE.getInterpolation(t)
            return from + (to - from) * e
        }

        fun done(now: Long) = now - start >= dur
    }

    private val anims = HashMap<LaidKey, Anim>()
    private val live = ArrayList<LaidKey>()
    private var seeking = false
        set(v) { if (field != v) { field = v; invalidate() } }

    private fun press(lk: LaidKey) {
        pressed += lk
        animate(lk, 1f, 70L, spring = false)
    }

    private fun unpress(lk: LaidKey) {
        pressed -= lk
        animate(lk, 0f, 170L, spring = true)
    }

    private fun animate(lk: LaidKey, to: Float, dur: Long, spring: Boolean) {
        val now = AnimationUtils.currentAnimationTimeMillis()
        val cur = anims[lk]?.value(now)?.coerceIn(0f, 1f) ?: 0f
        if (!ValueAnimator.areAnimatorsEnabled()) {
            if (to == 0f) anims.remove(lk) else anims[lk] = Anim(to, to, now, 1L, false)
        } else {
            anims[lk] = Anim(cur, to, now, dur, spring)
        }
        invalidate()
    }

    // ---------------------------------------------------------------- layout

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = padTop + padBottom + layout.rows.sumOf { (it.height * baseRowHeight).toDouble() }.toFloat()
        setMeasuredDimension(w, h.toInt())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        layoutKeys(right - left, bottom - top)
    }

    private fun layoutKeys(w: Int, h: Int) {
        laid.clear()
        hitKeys.clear()
        val padH = 3 * dp
        unit = (w - 2 * padH) / 10f
        var y = padTop
        val rows = layout.rows
        val adaptiveLayout = layout.kind == LayoutKind.LETTERS
        for ((ri, row) in rows.withIndex()) {
            val rh = row.height * baseRowHeight
            var x = padH + row.padStart * unit
            for ((ki, key) in row.keys.withIndex()) {
                val kw = key.width * unit
                val rect = RectF(x + gapH / 2, y + gapV / 2, x + kw - gapH / 2, y + rh - gapV / 2)
                val touch = RectF(
                    if (ki == 0) 0f else x,
                    if (ri == 0) 0f else y,
                    if (ki == row.keys.lastIndex) w.toFloat() else x + kw,
                    if (ri == rows.lastIndex) h.toFloat() else y + rh,
                )
                val hit = HitKey(
                    laid.size, key.code, touch.left, touch.top, touch.right, touch.bottom,
                    x + kw / 2, y + rh / 2, kw, rh, adaptiveLayout && key.isLetter,
                )
                laid += LaidKey(key, rect, touch, hit)
                hitKeys += hit
                x += kw
            }
            y += rh
        }
        hitMap = null
        anims.clear()
        staticDirty = true
    }

    // ---------------------------------------------------------------- drawing

    private fun icon(res: Int, color: Int): Drawable {
        val d = icons.getOrPut(res) { ContextCompat.getDrawable(context, res)!!.mutate() }
        d.setTint(color)
        return d
    }

    private fun iconFor(key: Key): Int? = when (key.code) {
        Codes.SHIFT -> when (shiftState) {
            ShiftState.OFF -> R.drawable.ic_shift
            ShiftState.ON -> R.drawable.ic_shift_on
            ShiftState.LOCKED -> R.drawable.ic_caps_lock
        }
        Codes.DELETE -> R.drawable.ic_backspace
        Codes.EMOJI -> R.drawable.ic_emoji
        Codes.ENTER -> when (enterAction) {
            EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search
            EditorInfo.IME_ACTION_SEND -> R.drawable.ic_send
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_arrow_forward
            EditorInfo.IME_ACTION_PREVIOUS -> R.drawable.ic_arrow_back
            EditorInfo.IME_ACTION_DONE -> R.drawable.ic_check
            else -> R.drawable.ic_enter
        }
        else -> null
    }

    private fun kindOf(k: Key): GlassRenderer.Kind = when {
        k.style == KeyStyle.ACTION -> GlassRenderer.Kind.ACCENT
        k.code == Codes.SHIFT && shiftState == ShiftState.LOCKED -> GlassRenderer.Kind.ACCENT
        k.style == KeyStyle.MODIFIER -> GlassRenderer.Kind.MODIFIER
        else -> GlassRenderer.Kind.GLASS
    }

    private fun ensureStatic() {
        if (width <= 0 || height <= 0) return
        var b = staticLayer
        if (b == null || b.width != width || b.height != height) {
            b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            staticLayer = b
            staticDirty = true
        }
        if (!staticDirty) return
        b!!.eraseColor(0)
        val c = Canvas(b)
        // Shadows first so no shadow ever lies over a neighbouring key.
        for (lk in laid) {
            val kind = kindOf(lk.key)
            if (keyBorders || kind == GlassRenderer.Kind.ACCENT) renderer.drawShadow(c, lk.rect, radius, kind)
        }
        for (lk in laid) drawKeyBody(c, lk, 0f)
        staticDirty = false
    }

    private fun drawKeyBody(c: Canvas, lk: LaidKey, press: Float) {
        val k = lk.key
        val r = lk.rect
        val kind = kindOf(k)
        renderer.drawGlass(c, r, radius, kind, press, keyBorders)
        val accent = kind == GlassRenderer.Kind.ACCENT
        val fg = if (accent) theme.onAccent else theme.text
        val shadowColor = if (accent) 0 else if (theme.isDark) 0x59000000 else 0x66FFFFFF
        textPaint.setShadowLayer(1.2f * dp, 0f, 0.6f * dp, shadowColor)
        val res = iconFor(k)
        if (res != null) {
            val s = (min(r.width(), r.height()) * 0.5f).toInt()
            val d = icon(res, fg)
            val l = (r.centerX() - s / 2f).toInt()
            val t = (r.centerY() - s / 2f).toInt()
            d.setBounds(l, t, l + s, t + s)
            d.draw(c)
        } else {
            val shifted = shiftState != ShiftState.OFF
            val isSpace = k.code == Codes.SPACE
            val label = when {
                isSpace && seeking && press > 0f -> "‹    ›"
                isSpace -> spaceLabel
                k.isLetter && shifted -> k.label.uppercase()
                else -> k.label
            }
            textPaint.color = if (isSpace) theme.hint else fg
            textPaint.textSize = when {
                isSpace -> r.height() * (if (seeking && press > 0f) 0.42f else 0.27f)
                label.length == 1 -> min(r.height() * 0.44f, r.width() * 0.68f)
                else -> r.height() * 0.29f
            }
            c.drawText(label, r.centerX(), r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
        }
        textPaint.clearShadowLayer()
        if (showHints && k.hint != null && layout.kind == LayoutKind.LETTERS) {
            hintPaint.color = theme.hint
            hintPaint.textSize = r.height() * 0.22f
            c.drawText(k.hint, r.right - 5 * dp, r.top + 3.5f * dp + hintPaint.textSize, hintPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = AnimationUtils.currentAnimationTimeMillis()
        ensureStatic()
        live.clear()
        live.addAll(anims.keys)
        staticLayer?.let { bmp ->
            if (live.isEmpty()) {
                canvas.drawBitmap(bmp, 0f, 0f, null)
            } else {
                canvas.save()
                // Clear the key and its resting shadow (which stays within the key gaps), so a
                // pressed key reads as sunk into the glass.
                for (lk in live) {
                    val r = lk.rect
                    canvas.clipOutRect(r.left - 2 * dp, r.top - 1 * dp, r.right + 2 * dp, r.bottom + 4.5f * dp)
                }
                canvas.drawBitmap(bmp, 0f, 0f, null)
                canvas.restore()
            }
        }
        var running = false
        for (lk in live) {
            val a = anims[lk] ?: continue
            val v = a.value(now)
            // Pressed keys sink to 95.5% and brighten; release springs slightly past 1.
            val scale = 1f - 0.045f * v
            canvas.save()
            canvas.scale(scale, scale, lk.rect.centerX(), lk.rect.centerY())
            drawKeyBody(canvas, lk, v.coerceIn(0f, 1f))
            canvas.restore()
            if (!a.done(now)) running = true else if (a.to == 0f) anims.remove(lk)
        }
        if (showHitMap && layout.kind == LayoutKind.LETTERS) {
            // Drawn over the keys (translucent) so the moved key edges are visible.
            drawHitMap(canvas)
            currentProbs()?.let { probs ->
                keyPaint.color = theme.accent
                for (lk in laid) {
                    if (!lk.key.isLetter) continue
                    val r = lk.rect
                    val bw = r.width() * 0.8f * sqrt(probs[lk.key.code - 'a'.code].toDouble()).toFloat()
                    canvas.drawRoundRect(r.centerX() - bw / 2, r.bottom - 5 * dp, r.centerX() + bw / 2, r.bottom - 2 * dp, dp, dp, keyPaint)
                }
            }
        }
        if (running) postInvalidateOnAnimation()
    }

    private var hitMap: Bitmap? = null
    private var hitMapKey: String? = null

    /** Debug overlay: tints every point where the effective target differs from the drawn key. */
    private fun drawHitMap(canvas: Canvas) {
        val probs = currentProbs() ?: return
        if (width <= 0 || height <= 0) return
        val key = "${listener?.currentPrefix()}|$width|$height|${resolver.strength}|${resolver.enabled}"
        var bmp = hitMap
        if (bmp == null || hitMapKey != key) {
            if (bmp == null || bmp.width != width || bmp.height != height) {
                bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }
            bmp!!.eraseColor(0)
            val bc = Canvas(bmp)
            val p = Paint().apply { color = (theme.accent and 0x00FFFFFF) or 0x55000000 }
            val step = max(4, (5 * dp).toInt())
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    val r = resolver.resolve(x + step / 2f, y + step / 2f, hitKeys, probs, consume = false)
                    if (r != null && r.stolen) bc.drawRect(x.toFloat(), y.toFloat(), (x + step).toFloat(), (y + step).toFloat(), p)
                    x += step
                }
                y += step
            }
            hitMap = bmp
            hitMapKey = key
        }
        canvas.drawBitmap(bmp, 0f, 0f, null)
    }

    private fun currentProbs(): FloatArray? =
        predictor?.takeIf { it.isReady }?.distribution(listener?.currentPrefix() ?: "")

    /** Call after the text context changed so the hit map can refresh. */
    fun onContextChanged() {
        if (showHitMap) invalidate()
    }

    // ---------------------------------------------------------------- touch

    private enum class Mode { NORMAL, POPUP, SEEK, SWIPE_DELETE, REPEAT, CONSUMED }

    private inner class Ptr(var lk: LaidKey, val downX: Float, val downY: Float, var stolen: Boolean) {
        var mode = Mode.NORMAL
        var x = downX
        var y = downY
        var seekAnchorX = downX
        var swipeWords = 0
        var repeatCount = 0
        var popupItems: List<String> = emptyList()
        var popupLeft = 0f
        var popupRight = 0f
        var popupCellW = 1f
        var popupFlip = false
        var popupSel = 0
        val longPress = Runnable { onLongPress(this) }
        val repeat = Runnable { onRepeat(this) }
    }

    private val ptrs = LinkedHashMap<Int, Ptr>()
    private var previewOwner: Ptr? = null
    private val hidePreviewRunnable = Runnable { overlay?.hidePreview() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Typing must never turn into a scroll of an enclosing list (settings preview).
                parent?.requestDisallowInterceptTouchEvent(true)
                // Rollover: a new finger lands while another is still on a character key ->
                // commit the earlier key now so fast two-thumb typing never drops letters.
                for (p in ptrs.values) {
                    if (p.mode == Mode.NORMAL && producesText(p.lk.key)) {
                        timer.removeCallbacks(p.longPress)
                        commitTap(p)
                        p.mode = Mode.CONSUMED
                        release(p)
                    }
                }
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    ptrs[e.getPointerId(i)]?.let { move(it, e.getX(i), e.getY(i)) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                ptrs.remove(e.getPointerId(e.actionIndex))?.let { up(it) }
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun producesText(k: Key) = k.code > 0

    private fun down(id: Int, x: Float, y: Float) {
        if (laid.isEmpty()) return
        val lk: LaidKey
        var stolen = false
        if (layout.kind == LayoutKind.LETTERS) {
            val r = resolver.resolve(x, y, hitKeys, currentProbs()) ?: return
            lk = laid[r.key.id]
            stolen = r.stolen
        } else {
            lk = laid[(resolver.geometric(x, y, hitKeys) ?: return).id]
        }
        val p = Ptr(lk, x, y, stolen)
        ptrs[id] = p
        listener?.feedback(lk.key.code)
        when (lk.key.code) {
            Codes.SHIFT -> {
                listener?.onKeyCode(Codes.SHIFT)
                p.mode = Mode.CONSUMED
            }
            Codes.DELETE -> {
                resolver.onBackspace()
                timer.postDelayed(p.repeat, 400)
            }
        }
        if (lk.key.popup.isNotEmpty()) timer.postDelayed(p.longPress, longPressDelay)
        if (lk.key.code == Codes.SPACE) timer.postDelayed(p.longPress, max(longPressDelay, 450L))
        press(lk)
        showPreviewFor(p)
        invalidate()
    }

    private fun move(p: Ptr, x: Float, y: Float) {
        p.x = x
        p.y = y
        val slop = 10 * dp
        when (p.mode) {
            Mode.NORMAL -> when (p.lk.key.code) {
                Codes.SPACE -> if (abs(x - p.downX) > slop * 1.5f) {
                    p.mode = Mode.SEEK
                    seeking = true
                    timer.removeCallbacks(p.longPress)
                    p.seekAnchorX = x
                }
                Codes.DELETE -> if (backspaceSwipe && p.downX - x > slop * 1.5f) {
                    p.mode = Mode.SWIPE_DELETE
                    timer.removeCallbacks(p.repeat)
                    updateSwipe(p)
                }
                else -> if (producesText(p.lk.key)) slideIfNeeded(p, x, y)
            }
            Mode.SEEK -> {
                val steps = ((x - p.seekAnchorX) / seekStepPx).toInt()
                if (steps != 0) {
                    listener?.onCursorMove(steps)
                    p.seekAnchorX += steps * seekStepPx
                }
            }
            Mode.SWIPE_DELETE -> updateSwipe(p)
            Mode.POPUP -> {
                val sel = popupIndexAt(p, x)
                if (sel != p.popupSel) {
                    p.popupSel = sel
                    overlay?.setSelection(sel)
                }
            }
            else -> {}
        }
    }

    /** If the finger slides well off its key, follow it to the key now under the finger. */
    private fun slideIfNeeded(p: Ptr, x: Float, y: Float) {
        val t = p.lk.touch
        val mx = unit * 0.3f
        if (x >= t.left - mx && x <= t.right + mx && y >= t.top - mx && y <= t.bottom + mx) return
        val g = resolver.geometric(x, y, hitKeys) ?: return
        val nk = laid[g.id]
        if (nk === p.lk || !producesText(nk.key) || nk.key.code == Codes.SPACE) return
        unpress(p.lk)
        p.lk = nk
        p.stolen = false
        press(nk)
        timer.removeCallbacks(p.longPress)
        if (nk.key.popup.isNotEmpty()) timer.postDelayed(p.longPress, longPressDelay)
        showPreviewFor(p)
        invalidate()
    }

    private fun updateSwipe(p: Ptr) {
        val dist = p.downX - p.x
        val slop = 10 * dp
        val n = if (dist < slop) 0 else 1 + ((dist - slop) / (unit * 0.9f)).toInt()
        if (n != p.swipeWords) {
            p.swipeWords = n
            listener?.onDeleteSwipe(n)
        }
    }

    private fun up(p: Ptr) {
        timer.removeCallbacks(p.longPress)
        timer.removeCallbacks(p.repeat)
        when (p.mode) {
            Mode.NORMAL -> commitTap(p)
            Mode.POPUP -> {
                p.popupItems.getOrNull(p.popupSel)?.let { listener?.onText(it, false) }
                overlay?.hidePanel()
            }
            Mode.SWIPE_DELETE -> listener?.onDeleteSwipeEnd(p.swipeWords)
            else -> {}
        }
        release(p)
    }

    private fun commitTap(p: Ptr) {
        val k = p.lk.key
        when {
            k.code == Codes.SHIFT -> {}
            k.code < 0 -> listener?.onKeyCode(k.code)
            k.code == Codes.SPACE -> listener?.onKeyCode(Codes.SPACE)
            else -> {
                if (k.isLetter && layout.kind == LayoutKind.LETTERS) resolver.recordTap(p.lk.hit, p.downX, p.downY, p.stolen)
                listener?.onText(k.output(shiftState != ShiftState.OFF), k.isLetter)
            }
        }
    }

    private fun release(p: Ptr) {
        if (p.mode == Mode.SEEK) seeking = false
        unpress(p.lk)
        if (previewOwner === p) {
            previewOwner = null
            timer.removeCallbacks(hidePreviewRunnable)
            timer.postDelayed(hidePreviewRunnable, 40)
        }
        invalidate()
    }

    private fun showPreviewFor(p: Ptr) {
        val k = p.lk.key
        val o = overlay ?: return
        if (!showPreview || !producesText(k) || k.code == Codes.SPACE) {
            if (previewOwner != null) o.hidePreview()
            previewOwner = null
            return
        }
        syncOverlayOffset()
        val r = p.lk.rect
        val w = max(r.width() * 1.15f, 44 * dp)
        val h = min(r.height(), 46 * dp)
        val left = (r.centerX() - w / 2).coerceIn(2 * dp, width - w - 2 * dp)
        val label = if (k.isLetter && shiftState != ShiftState.OFF) k.label.uppercase() else k.label
        timer.removeCallbacks(hidePreviewRunnable)
        o.showPreview(RectF(left, r.top - h - 2 * dp, left + w, r.top - 2 * dp), label)
        previewOwner = p
    }

    private fun syncOverlayOffset() {
        val o = overlay ?: return
        // Walk up to the overlay's parent (works before/without window attachment, too).
        var x = 0f
        var y = 0f
        var v: View = this
        while (v !== o.parent) {
            x += v.left + v.translationX
            y += v.top + v.translationY
            v = v.parent as? View ?: return
        }
        o.offsetX = x - o.left
        o.offsetY = y - o.top
    }

    private fun onLongPress(p: Ptr) {
        if (p.mode != Mode.NORMAL) return
        val k = p.lk.key
        if (k.code == Codes.SPACE) {
            p.mode = Mode.CONSUMED
            listener?.onSpaceLongPress()
            release(p)
            return
        }
        if (k.popup.isEmpty()) return
        val shifted = shiftState != ShiftState.OFF
        val items = if (shifted && k.isLetter) k.popup.map { if (it.length == 1 && it[0].isLetter()) it.uppercase() else it } else k.popup
        p.mode = Mode.POPUP
        p.popupItems = items
        listener?.feedback(Codes.SHIFT)
        val r = p.lk.rect
        val cellW = max(r.width() * 0.95f, 34 * dp)
        val cellH = min(r.height() * 0.9f, 44 * dp)
        val pad = 4 * dp
        val total = cellW * items.size
        var left = r.left - pad
        var flip = false
        if (left + total + 2 * pad > width - 2 * dp) {
            // Not enough room to the right: grow leftwards from the key, first item still over it.
            flip = true
            left = max(2 * dp, r.right + pad - total - 2 * pad)
        }
        val top = r.top - cellH - 2 * pad - 2 * dp
        val panel = RectF(left, top, min(width - 2 * dp, left + total + 2 * pad), top + cellH + 2 * pad)
        val cells = items.indices.map { i ->
            val slot = if (flip) items.size - 1 - i else i
            val cl = panel.left + pad + slot * cellW
            RectF(cl, top + pad, cl + cellW, top + pad + cellH)
        }
        p.popupLeft = panel.left + pad
        p.popupRight = panel.left + pad + total
        p.popupCellW = cellW
        p.popupFlip = flip
        p.popupSel = 0
        syncOverlayOffset()
        overlay?.showPanel(panel, cells, items, 0)
        previewOwner = null
    }

    private fun popupIndexAt(p: Ptr, x: Float): Int {
        val raw = if (p.popupFlip) ((p.popupRight - x) / p.popupCellW) else ((x - p.popupLeft) / p.popupCellW)
        return raw.toInt().coerceIn(0, p.popupItems.size - 1)
    }

    private fun onRepeat(p: Ptr) {
        if (p.mode != Mode.NORMAL && p.mode != Mode.REPEAT) return
        p.mode = Mode.REPEAT
        p.repeatCount++
        val wordMode = p.repeatCount > 18
        listener?.onKeyCode(if (wordMode) Codes.DELETE_WORD else Codes.DELETE)
        resolver.onBackspace()
        timer.postDelayed(p.repeat, if (wordMode) 140L else max(30L, 85L - p.repeatCount * 4L))
    }

    /** A word was finished outside the IME service (settings preview): let the resolver learn. */
    fun resolverCommitWord() = resolver.commitWord()

    /** Visual bounds of the first key with [code] (used by tests and tooling). */
    fun keyRect(code: Int): RectF? = laid.firstOrNull { it.key.code == code }?.rect?.let { RectF(it) }

    fun cancelAll() {
        for (p in ptrs.values) {
            timer.removeCallbacks(p.longPress)
            timer.removeCallbacks(p.repeat)
            if (p.mode == Mode.SWIPE_DELETE) listener?.onDeleteSwipeEnd(0)
        }
        ptrs.clear()
        pressed.clear()
        anims.clear()
        seeking = false
        previewOwner = null
        overlay?.hideAll()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    companion object {
        private val EASE = DecelerateInterpolator(1.6f)
        private val SPRING = OvershootInterpolator(2.2f)
    }
}
