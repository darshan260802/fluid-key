package dev.fluidkey.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils

/**
 * A frosted-glass palette: a soft gradient backdrop with two aurora glows, translucent keys
 * with an inner sheen and a hairline edge, and an accent gradient for action keys.
 */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val backdropTop: Int,
    val backdropBottom: Int,
    val aurora1: Int,
    val aurora2: Int,
    val glass: Int,
    val glassModifier: Int,
    val glassPressed: Int,
    val sheen: Int,
    val hairlineTop: Int,
    val hairlineBottom: Int,
    val shadow: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val accentEnd: Int,
    val onAccent: Int,
    val accentGlow: Int,
    val popup: Int,
    val popupText: Int,
    val isDark: Boolean,
) {
    // Flat equivalents for plain views (ripples, list rows, fallbacks).
    val background: Int get() = backdropBottom
    val key: Int get() = ColorUtils.compositeColors(glass, backdropBottom)
    val modifier: Int get() = ColorUtils.compositeColors(glassModifier, backdropBottom)
    val pressed: Int get() = ColorUtils.compositeColors(glassPressed, backdropBottom)
}

object Themes {
    private fun c(v: Long) = v.toInt()
    private fun alpha(color: Int, a: Float) = ColorUtils.setAlphaComponent(color, (a * 255).toInt())

    private fun dark(
        id: String, name: String, top: Long, bottom: Long, a1: Long, a2: Long, accent: Long, accentEnd: Long,
        onAccent: Long = 0xFF0B1020,
    ): KeyboardTheme {
        val acc = c(accent)
        return KeyboardTheme(
            id = id, name = name,
            backdropTop = c(top), backdropBottom = c(bottom),
            aurora1 = alpha(c(a1), 0.42f), aurora2 = alpha(c(a2), 0.32f),
            glass = c(0x24FFFFFF), glassModifier = c(0x12FFFFFF), glassPressed = c(0x4DFFFFFF),
            sheen = c(0x29FFFFFF), hairlineTop = c(0x52FFFFFF), hairlineBottom = c(0x0AFFFFFF),
            shadow = c(0x73000000),
            text = c(0xFFF5F7FA), hint = c(0x99FFFFFF),
            accent = acc, accentEnd = c(accentEnd), onAccent = c(onAccent), accentGlow = alpha(acc, 0.55f),
            popup = ColorUtils.blendARGB(c(top), c(0xFFFFFFFF), 0.16f), popupText = c(0xFFFFFFFF),
            isDark = true,
        )
    }

    private fun light(
        id: String, name: String, top: Long, bottom: Long, a1: Long, a2: Long, accent: Long, accentEnd: Long,
        text: Long = 0xFF1C2230,
    ): KeyboardTheme {
        val acc = c(accent)
        return KeyboardTheme(
            id = id, name = name,
            backdropTop = c(top), backdropBottom = c(bottom),
            aurora1 = alpha(c(a1), 0.38f), aurora2 = alpha(c(a2), 0.30f),
            glass = c(0xB8FFFFFF), glassModifier = c(0x5CFFFFFF), glassPressed = c(0xFFFFFFFF),
            sheen = c(0x99FFFFFF), hairlineTop = c(0xFFFFFFFF), hairlineBottom = c(0x14000000),
            shadow = c(0x2E1C2230),
            text = c(text), hint = ColorUtils.setAlphaComponent(c(text), 0x8C),
            accent = acc, accentEnd = c(accentEnd), onAccent = c(0xFFFFFFFF), accentGlow = alpha(acc, 0.40f),
            popup = c(0xFFFFFFFF), popupText = c(text),
            isDark = false,
        )
    }

    val DARK = dark("dark", "Midnight", 0xFF1B2233, 0xFF0D1119, 0xFF3D5AFE, 0xFF8E5CFF, 0xFF9DB8FF, 0xFF6C7CFF)
    val OCEAN = dark("ocean", "Ocean", 0xFF0B2A3C, 0xFF06131D, 0xFF00BCD4, 0xFF2979FF, 0xFF6FE3F0, 0xFF3D8BFF)
    val FOREST = dark("forest", "Forest", 0xFF12291F, 0xFF07130D, 0xFF2E9E5B, 0xFF00BFA5, 0xFF9BE3A8, 0xFF2BBF9C)
    val SUNSET = dark("sunset", "Sunset", 0xFF2C1622, 0xFF13090F, 0xFFFF7043, 0xFFEC407A, 0xFFFFC07A, 0xFFFF7A59)
    val LIGHT = light("light", "Daylight", 0xFFF1F4FA, 0xFFDCE2EC, 0xFF90CAF9, 0xFFD1A6F0, 0xFF1A73E8, 0xFF5146E5)
    val LAVENDER = light("lavender", "Lavender", 0xFFF3EEFB, 0xFFE0D6F2, 0xFFB39DDB, 0xFFF4A3C4, 0xFF7E57C2, 0xFF5E35B1, 0xFF2A1A52)
    val ROSE = light("rose", "Rose", 0xFFFDF0F4, 0xFFF2DAE3, 0xFFF48FB1, 0xFFFFC98F, 0xFFD81B60, 0xFFAD1457, 0xFF3D0F22)

    val AMOLED = KeyboardTheme(
        id = "amoled", name = "AMOLED",
        backdropTop = c(0xFF000000), backdropBottom = c(0xFF000000),
        aurora1 = c(0x00000000), aurora2 = c(0x00000000),
        glass = c(0x17FFFFFF), glassModifier = c(0x0AFFFFFF), glassPressed = c(0x3DFFFFFF),
        sheen = c(0x14FFFFFF), hairlineTop = c(0x47FFFFFF), hairlineBottom = c(0x08FFFFFF),
        shadow = c(0x00000000),
        text = c(0xFFFFFFFF), hint = c(0x8CFFFFFF),
        accent = c(0xFF9DB8FF), accentEnd = c(0xFF6C7CFF), onAccent = c(0xFF000000), accentGlow = c(0x669DB8FF),
        popup = c(0xFF1C1C1E), popupText = c(0xFFFFFFFF),
        isDark = true,
    )

    /** Themes shown in the picker, in order. "system*" and "dynamic" are resolved at runtime. */
    val PICKER = listOf("system", "system_amoled", "dynamic", "light", "dark", "amoled", "ocean", "forest", "sunset", "lavender", "rose")

    fun label(id: String): String = when (id) {
        "system" -> "Auto"
        "system_amoled" -> "Auto AMOLED"
        "dynamic" -> "Material You"
        else -> byId(id)?.name ?: id
    }

    private fun byId(id: String) = when (id) {
        "light" -> LIGHT
        "dark" -> DARK
        "amoled" -> AMOLED
        "ocean" -> OCEAN
        "forest" -> FOREST
        "sunset" -> SUNSET
        "lavender" -> LAVENDER
        "rose" -> ROSE
        else -> null
    }

    fun resolve(context: Context, id: String): KeyboardTheme {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        byId(id)?.let { return it }
        return when (id) {
            "dynamic" -> if (Build.VERSION.SDK_INT >= 31) dynamic(context, night) else if (night) DARK else LIGHT
            "system_amoled" -> if (night) AMOLED else LIGHT
            else -> if (night) DARK else LIGHT
        }
    }

    @RequiresApi(31)
    private fun dynamic(context: Context, night: Boolean): KeyboardTheme {
        fun col(id: Int) = context.getColor(id)
        return if (night) {
            DARK.copy(
                id = "dynamic", name = "Material You",
                backdropTop = col(android.R.color.system_neutral2_800),
                backdropBottom = col(android.R.color.system_neutral1_900),
                aurora1 = alpha(col(android.R.color.system_accent1_400), 0.40f),
                aurora2 = alpha(col(android.R.color.system_accent3_400), 0.30f),
                accent = col(android.R.color.system_accent1_200),
                accentEnd = col(android.R.color.system_accent3_300),
                onAccent = col(android.R.color.system_accent1_900),
                accentGlow = alpha(col(android.R.color.system_accent1_200), 0.5f),
                popup = col(android.R.color.system_neutral2_700),
            )
        } else {
            LIGHT.copy(
                id = "dynamic", name = "Material You",
                backdropTop = col(android.R.color.system_neutral2_50),
                backdropBottom = col(android.R.color.system_neutral2_100),
                aurora1 = alpha(col(android.R.color.system_accent1_200), 0.40f),
                aurora2 = alpha(col(android.R.color.system_accent3_200), 0.32f),
                text = col(android.R.color.system_neutral1_900),
                hint = alpha(col(android.R.color.system_neutral1_900), 0.55f),
                accent = col(android.R.color.system_accent1_600),
                accentEnd = col(android.R.color.system_accent3_600),
                accentGlow = alpha(col(android.R.color.system_accent1_600), 0.4f),
                popupText = col(android.R.color.system_neutral1_900),
            )
        }
    }
}
