package dev.fluidkey.ime

import android.content.SharedPreferences

/** Typed access to the user's settings (keys match res/xml/preferences.xml). */
class Prefs(private val sp: SharedPreferences) {
    val theme: String get() = sp.getString("theme", "system") ?: "system"
    val keyBorders get() = sp.getBoolean("key_borders", true)
    val heightScale get() = sp.getInt("height_scale", 100) / 100f
    val oneHanded: String get() = sp.getString("one_handed", "off") ?: "off"
    val numberRow get() = sp.getBoolean("number_row", false)
    val showHints get() = sp.getBoolean("show_hints", true)
    val longPressDelay get() = sp.getInt("long_press_delay", 300).toLong()
    val autoCap get() = sp.getBoolean("auto_cap", true)
    val doubleSpacePeriod get() = sp.getBoolean("double_space_period", true)
    val keyPreview get() = sp.getBoolean("key_preview", true)
    val haptics get() = sp.getBoolean("haptics", true)
    val sound get() = sp.getBoolean("sound", false)
    val adaptive get() = sp.getBoolean("adaptive_targets", true)
    /** Slider 0..100 -> lambda 0..2 (default 50 -> 1.0). */
    val adaptiveStrength get() = sp.getInt("adaptive_strength", 50) / 50f
    val adaptiveLearning get() = sp.getBoolean("adaptive_learning", true)
    val showHitMap get() = sp.getBoolean("show_hit_map", false)
    val spaceSeekSensitivity get() = sp.getInt("space_seek_sensitivity", 50)
    val backspaceSwipe get() = sp.getBoolean("backspace_swipe", true)
    val clipboardHistory get() = sp.getBoolean("clipboard_history", true)
}
