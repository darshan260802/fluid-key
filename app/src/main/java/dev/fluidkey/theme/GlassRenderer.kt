package dev.fluidkey.theme

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/**
 * Draws FluidKey's frosted-glass surfaces: the aurora backdrop and glass shapes
 * (keys, popups, chips). Shared by the keyboard, its panels and the settings app.
 */
class GlassRenderer(private val density: Float) {

    var theme: KeyboardTheme = Themes.DARK
        set(v) {
            if (field != v) {
                field = v
                rebuildShaders()
            }
        }

    enum class Kind { GLASS, MODIFIER, ACCENT }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val tmp = RectF()

    // Unit (0..1) gradients, positioned per shape with a local matrix: no per-key allocation.
    private lateinit var sheenShader: LinearGradient
    private lateinit var hairShader: LinearGradient
    private lateinit var accentShader: LinearGradient

    init {
        rebuildShaders()
    }

    private fun rebuildShaders() {
        val t = theme
        sheenShader = LinearGradient(0f, 0f, 0f, 1f, intArrayOf(t.sheen, ColorUtils.setAlphaComponent(t.sheen, 0)), floatArrayOf(0f, 0.55f), Shader.TileMode.CLAMP)
        hairShader = LinearGradient(0f, 0f, 0f, 1f, t.hairlineTop, t.hairlineBottom, Shader.TileMode.CLAMP)
        accentShader = LinearGradient(0f, 0f, 1f, 1f, t.accent, t.accentEnd, Shader.TileMode.CLAMP)
        sheen.shader = sheenShader
        hair.shader = hairShader
        accentFill.shader = accentShader
        shadow.color = t.shadow
        shadow.maskFilter = if (Color.alpha(t.shadow) > 0) BlurMaskFilter(3.5f * density, BlurMaskFilter.Blur.NORMAL) else null
        glow.color = t.accentGlow
        glow.maskFilter = BlurMaskFilter(7f * density, BlurMaskFilter.Blur.NORMAL)
    }

    private object Color {
        fun alpha(c: Int) = (c ushr 24) and 0xFF
    }

    private fun place(shader: Shader, r: RectF, diagonal: Boolean = false) {
        m.reset()
        m.setScale(if (diagonal) r.width() else 1f, r.height())
        m.postTranslate(if (diagonal) r.left else 0f, r.top)
        shader.setLocalMatrix(m)
    }

    /** Paints the aurora backdrop for an area of [w] x [h]. */
    fun drawBackdrop(c: Canvas, w: Int, h: Int) {
        val t = theme
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w * 0.35f, h.toFloat(), t.backdropTop, t.backdropBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        if (Color.alpha(t.aurora1) > 0) {
            val r1 = w * 0.55f
            p.shader = RadialGradient(w * 0.12f, h * 0.18f, r1, t.aurora1, ColorUtils.setAlphaComponent(t.aurora1, 0), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        if (Color.alpha(t.aurora2) > 0) {
            val r2 = w * 0.6f
            p.shader = RadialGradient(w * 0.92f, h * 0.85f, r2, t.aurora2, ColorUtils.setAlphaComponent(t.aurora2, 0), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
    }

    /**
     * Shadow (and accent glow) beneath a shape. Uses BlurMaskFilter, so call it on a software
     * canvas (cached layers); it is skipped for live, hardware-drawn pressed keys.
     */
    fun drawShadow(c: Canvas, r: RectF, radius: Float, kind: Kind) {
        if (kind == Kind.ACCENT) {
            tmp.set(r)
            tmp.inset(-1f * density, -0.5f * density)
            tmp.offset(0f, 1.5f * density)
            c.drawRoundRect(tmp, radius, radius, glow)
        } else if (shadow.maskFilter != null && kind == Kind.GLASS) {
            tmp.set(r)
            tmp.offset(0f, 1.6f * density)
            c.drawRoundRect(tmp, radius, radius, shadow)
        }
    }

    /**
     * A glass shape. [press] 0..1 blends the fill toward the pressed (brighter) glass and adds a
     * soft accent tint for accent shapes.
     */
    fun drawGlass(c: Canvas, r: RectF, radius: Float, kind: Kind, press: Float = 0f, borders: Boolean = true) {
        val t = theme
        if (kind == Kind.ACCENT) {
            place(accentShader, r, diagonal = true)
            accentFill.alpha = 255
            c.drawRoundRect(r, radius, radius, accentFill)
            if (press > 0f) {
                fill.color = ColorUtils.setAlphaComponent(0xFFFFFFFF.toInt(), (press * 60).toInt())
                c.drawRoundRect(r, radius, radius, fill)
            }
        } else {
            if (!borders && press <= 0.01f) return
            val base = if (kind == Kind.MODIFIER) t.glassModifier else t.glass
            fill.color = if (press > 0f) ColorUtils.blendARGB(base, t.glassPressed, press) else base
            c.drawRoundRect(r, radius, radius, fill)
        }
        if (!borders && kind != Kind.ACCENT) return
        place(sheenShader, r)
        c.drawRoundRect(r, radius, radius, sheen)
        tmp.set(r)
        tmp.inset(hair.strokeWidth / 2, hair.strokeWidth / 2)
        place(hairShader, tmp)
        c.drawRoundRect(tmp, radius, radius, hair)
    }

    /** Opaque glass bubble for popups (previews, long-press panels). */
    fun drawPopup(c: Canvas, r: RectF, radius: Float, alpha: Int = 255) {
        val t = theme
        fill.color = ColorUtils.setAlphaComponent(t.popup, (Color.alpha(t.popup) * alpha / 255))
        c.drawRoundRect(r, radius, radius, fill)
        sheen.alpha = alpha
        place(sheenShader, r)
        c.drawRoundRect(r, radius, radius, sheen)
        sheen.alpha = 255
        tmp.set(r)
        tmp.inset(hair.strokeWidth / 2, hair.strokeWidth / 2)
        place(hairShader, tmp)
        hair.alpha = alpha
        c.drawRoundRect(tmp, radius, radius, hair)
        hair.alpha = 255
    }

    /** Backdrop rendered to a bitmap (used as the input view background). */
    fun backdropBitmap(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        drawBackdrop(Canvas(b), w, h)
        return b
    }
}

/** A Drawable that paints the theme's aurora backdrop, cached per size. */
class BackdropDrawable(private val renderer: GlassRenderer) : Drawable() {
    private var cache: Bitmap? = null

    override fun onBoundsChange(bounds: Rect) {
        cache = null
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val bmp = cache ?: renderer.backdropBitmap(b.width(), b.height()).also { cache = it }
        canvas.drawBitmap(bmp, b.left.toFloat(), b.top.toFloat(), null)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

/** Rounded glass surface as a Drawable, for toolbar chips, panel cards and buttons. */
class GlassDrawable(
    private val renderer: GlassRenderer,
    private val radiusPx: Float,
    var kind: GlassRenderer.Kind = GlassRenderer.Kind.GLASS,
) : Drawable() {
    private val r = RectF()
    var press = 0f
        set(v) { field = v; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        r.set(bounds)
        renderer.drawGlass(canvas, r, minOf(radiusPx, r.height() / 2), kind, press)
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val pressed = state.contains(android.R.attr.state_pressed)
        val target = if (pressed) 1f else 0f
        if (press != target) {
            press = target
            return true
        }
        return false
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
