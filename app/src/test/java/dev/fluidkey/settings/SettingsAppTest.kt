package dev.fluidkey.settings

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.fluidkey.ime.Dictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2300dp-xhdpi")
class SettingsAppTest {

    @get:Rule
    val compose = createAndroidComposeRule<SettingsActivity>()

    private fun settle() {
        // Let the preview's dictionary load and the UI settle.
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(25)
        }
    }

    private fun shot(name: String) {
        settle()
        val root = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun rendersAndWritesSettingsTheKeyboardReads() {
        compose.onNodeWithText("FluidKey").assertExists()
        compose.onNodeWithText("Get started").assertExists()
        compose.onNodeWithText("Try it").assertExists()
        shot("settings_light")

        val sp = Dictionary.settings(compose.activity)
        compose.onNodeWithText("Number row").performScrollTo().performClick()
        settle()
        assertTrue(sp.getBoolean("number_row", false))

        compose.onNodeWithContentDescription("Daylight").performClick()
        settle()
        assertEquals("light", sp.getString("theme", null))
    }

    @Test
    @Config(qualifiers = "w411dp-h2300dp-night-xhdpi")
    fun darkScreenshot() {
        compose.onNodeWithText("Fluid touch").assertExists()
        shot("settings_dark")
    }
}
