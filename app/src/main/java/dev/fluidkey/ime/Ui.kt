package dev.fluidkey.ime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import dev.fluidkey.theme.GlassDrawable
import dev.fluidkey.theme.GlassRenderer
import dev.fluidkey.theme.KeyboardTheme

/** Small helpers for building the keyboard's programmatic glass UI. */
object Ui {
    fun dp(context: Context, v: Float) = (v * context.resources.displayMetrics.density).toInt()

    val medium: Typeface
        get() = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun ripple(theme: KeyboardTheme): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(theme.glassPressed), null, null)

    fun rounded(color: Int, radiusPx: Float) = GradientDrawable().apply {
        cornerRadius = radiusPx
        setColor(color)
    }

    /** A glass surface, optionally inset so a chip sits inside a larger touch target. */
    fun glass(
        context: Context,
        renderer: GlassRenderer,
        radiusDp: Float,
        kind: GlassRenderer.Kind = GlassRenderer.Kind.GLASS,
        insetDp: Float = 0f,
    ): Drawable {
        val g = GlassDrawable(renderer, dp(context, radiusDp).toFloat(), kind)
        val inset = dp(context, insetDp)
        return if (inset > 0) InsetDrawable(g, inset) else g
    }

    /** Circular glass chip with an icon. */
    fun iconButton(
        context: Context,
        renderer: GlassRenderer,
        icon: Int,
        desc: String,
        chip: Boolean = true,
        onClick: () -> Unit,
    ) = ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(renderer.theme.text)
        imageAlpha = 0xD9
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = desc
        background = if (chip) glass(context, renderer, 999f, insetDp = 5f) else ripple(renderer.theme)
        setOnClickListener { onClick() }
    }

    /** Marks a toolbar chip as active (its panel is open) with the accent glass. */
    fun setActive(button: ImageView, renderer: GlassRenderer, active: Boolean) {
        val inset = button.background as? InsetDrawable
        val g = (inset?.drawable ?: button.background) as? GlassDrawable ?: return
        g.kind = if (active) GlassRenderer.Kind.ACCENT else GlassRenderer.Kind.GLASS
        g.invalidateSelf()
        button.imageTintList = ColorStateList.valueOf(if (active) renderer.theme.onAccent else renderer.theme.text)
    }

    /** Glass pill with a label. */
    fun textButton(context: Context, renderer: GlassRenderer, label: String, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(renderer.theme.text)
            typeface = medium
            textSize = 14f
            gravity = Gravity.CENTER
            // Background first: setting a drawable resets padding to the drawable's own.
            background = glass(context, renderer, 999f, insetDp = 5f)
            val p = dp(context, 20f)
            setPadding(p, 0, p, 0)
            setOnClickListener { onClick() }
        }
}
