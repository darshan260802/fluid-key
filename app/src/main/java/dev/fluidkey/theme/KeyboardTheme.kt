package dev.fluidkey.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi

data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val modifier: Int,
    val pressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val onAccent: Int,
    val popup: Int,
    val popupText: Int,
    val isDark: Boolean,
)

object Themes {
    private fun c(v: Long) = v.toInt()

    val LIGHT = KeyboardTheme(
        background = c(0xFFE8EAED), key = c(0xFFFFFFFF), modifier = c(0xFFCDD1D6), pressed = c(0xFFB9BEC5),
        text = c(0xFF202124), hint = c(0xFF5F6368), accent = c(0xFF1A73E8), onAccent = c(0xFFFFFFFF),
        popup = c(0xFFFFFFFF), popupText = c(0xFF202124), isDark = false,
    )
    val DARK = KeyboardTheme(
        background = c(0xFF202124), key = c(0xFF3C4043), modifier = c(0xFF2D2E31), pressed = c(0xFF5F6368),
        text = c(0xFFE8EAED), hint = c(0xFF9AA0A6), accent = c(0xFF8AB4F8), onAccent = c(0xFF202124),
        popup = c(0xFF4A4E52), popupText = c(0xFFFFFFFF), isDark = true,
    )
    val AMOLED = KeyboardTheme(
        background = c(0xFF000000), key = c(0xFF111111), modifier = c(0xFF0A0A0A), pressed = c(0xFF2A2A2A),
        text = c(0xFFFFFFFF), hint = c(0xFF8A8A8A), accent = c(0xFF8AB4F8), onAccent = c(0xFF000000),
        popup = c(0xFF1E1E1E), popupText = c(0xFFFFFFFF), isDark = true,
    )
    val OCEAN = KeyboardTheme(
        background = c(0xFF0D1B2A), key = c(0xFF1B2D44), modifier = c(0xFF142236), pressed = c(0xFF2B4466),
        text = c(0xFFE3F2FD), hint = c(0xFF90A4BE), accent = c(0xFF64B5F6), onAccent = c(0xFF0D1B2A),
        popup = c(0xFF24405F), popupText = c(0xFFFFFFFF), isDark = true,
    )
    val FOREST = KeyboardTheme(
        background = c(0xFF0F1F17), key = c(0xFF1E3A2B), modifier = c(0xFF173022), pressed = c(0xFF2E5640),
        text = c(0xFFE8F5E9), hint = c(0xFF9CC3A8), accent = c(0xFF81C784), onAccent = c(0xFF0F1F17),
        popup = c(0xFF2A4A37), popupText = c(0xFFFFFFFF), isDark = true,
    )
    val SUNSET = KeyboardTheme(
        background = c(0xFF24150E), key = c(0xFF44281A), modifier = c(0xFF351F14), pressed = c(0xFF613A26),
        text = c(0xFFFFF3E0), hint = c(0xFFD7A98A), accent = c(0xFFFFB74D), onAccent = c(0xFF24150E),
        popup = c(0xFF573524), popupText = c(0xFFFFFFFF), isDark = true,
    )
    val LAVENDER = KeyboardTheme(
        background = c(0xFFEDE7F6), key = c(0xFFFFFFFF), modifier = c(0xFFD1C4E9), pressed = c(0xFFB39DDB),
        text = c(0xFF311B92), hint = c(0xFF7E6BA8), accent = c(0xFF7E57C2), onAccent = c(0xFFFFFFFF),
        popup = c(0xFFFFFFFF), popupText = c(0xFF311B92), isDark = false,
    )
    val ROSE = KeyboardTheme(
        background = c(0xFFFCE4EC), key = c(0xFFFFFFFF), modifier = c(0xFFF8BBD0), pressed = c(0xFFF48FB1),
        text = c(0xFF4A1427), hint = c(0xFF9C5A72), accent = c(0xFFD81B60), onAccent = c(0xFFFFFFFF),
        popup = c(0xFFFFFFFF), popupText = c(0xFF4A1427), isDark = false,
    )

    fun resolve(context: Context, id: String): KeyboardTheme {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return when (id) {
            "light" -> LIGHT
            "dark" -> DARK
            "amoled" -> AMOLED
            "ocean" -> OCEAN
            "forest" -> FOREST
            "sunset" -> SUNSET
            "lavender" -> LAVENDER
            "rose" -> ROSE
            "dynamic" -> if (Build.VERSION.SDK_INT >= 31) dynamic(context, night) else if (night) DARK else LIGHT
            "system_amoled" -> if (night) AMOLED else LIGHT
            else -> if (night) DARK else LIGHT
        }
    }

    @RequiresApi(31)
    private fun dynamic(context: Context, night: Boolean): KeyboardTheme {
        fun col(id: Int) = context.getColor(id)
        return if (night) KeyboardTheme(
            background = col(android.R.color.system_neutral1_900),
            key = col(android.R.color.system_neutral2_800),
            modifier = col(android.R.color.system_neutral2_900),
            pressed = col(android.R.color.system_neutral2_600),
            text = col(android.R.color.system_neutral1_50),
            hint = col(android.R.color.system_neutral2_300),
            accent = col(android.R.color.system_accent1_200),
            onAccent = col(android.R.color.system_accent1_800),
            popup = col(android.R.color.system_neutral2_700),
            popupText = col(android.R.color.system_neutral1_50),
            isDark = true,
        ) else KeyboardTheme(
            background = col(android.R.color.system_neutral2_100),
            key = col(android.R.color.system_neutral1_10),
            modifier = col(android.R.color.system_accent2_100),
            pressed = col(android.R.color.system_accent2_200),
            text = col(android.R.color.system_neutral1_900),
            hint = col(android.R.color.system_neutral2_600),
            accent = col(android.R.color.system_accent1_600),
            onAccent = col(android.R.color.system_accent1_0),
            popup = col(android.R.color.system_neutral1_10),
            popupText = col(android.R.color.system_neutral1_900),
            isDark = false,
        )
    }
}
