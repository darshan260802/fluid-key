package dev.fluidkey.ime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import dev.fluidkey.theme.KeyboardTheme

/** Small helpers for building the keyboard's programmatic UI. */
object Ui {
    fun dp(context: Context, v: Float) = (v * context.resources.displayMetrics.density).toInt()

    fun ripple(theme: KeyboardTheme, radiusDp: Float = 0f, context: Context? = null): RippleDrawable {
        val mask = if (radiusDp > 0 && context != null) GradientDrawable().apply {
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(0xFFFFFFFF.toInt())
        } else null
        return RippleDrawable(ColorStateList.valueOf(theme.pressed), null, mask)
    }

    fun rounded(color: Int, radiusPx: Float) = GradientDrawable().apply {
        cornerRadius = radiusPx
        setColor(color)
    }

    fun iconButton(context: Context, theme: KeyboardTheme, icon: Int, desc: String, onClick: () -> Unit) =
        ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(theme.hint)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = desc
            background = ripple(theme)
            setOnClickListener { onClick() }
        }

    fun textButton(context: Context, theme: KeyboardTheme, label: String, onClick: () -> Unit) =
        TextView(context).apply {
            text = label
            setTextColor(theme.text)
            textSize = 14f
            gravity = Gravity.CENTER
            val p = dp(context, 14f)
            setPadding(p, 0, p, 0)
            background = ripple(theme)
            setOnClickListener { onClick() }
        }
}
