package dev.fluidkey.ime

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import dev.fluidkey.clipboard.ClipboardPanel
import dev.fluidkey.emoji.EmojiPanel
import dev.fluidkey.keyboard.Codes
import dev.fluidkey.keyboard.KeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Drives the real IME service against a real EditText: touches go into KeyboardView,
 * text comes out of the editor. Also renders screenshots into build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class KeyboardEndToEndTest {

    class TestService : FluidKeyService() {
        var ic: InputConnection? = null
        var info: EditorInfo? = null
        override fun getCurrentInputConnection(): InputConnection? = ic
        override fun getCurrentInputEditorInfo(): EditorInfo? = info
    }

    private lateinit var service: TestService
    private lateinit var edit: EditText
    private lateinit var root: View
    private lateinit var kv: KeyboardView

    private fun idle(ms: Long = 0) {
        if (ms > 0) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        else shadowOf(Looper.getMainLooper()).idle()
    }

    private fun start(prefs: Map<String, Any> = emptyMap()) {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        val sp = Dictionary.settings(ctx).edit().clear()
        prefs.forEach { (k, v) ->
            when (v) {
                is Boolean -> sp.putBoolean(k, v)
                is Int -> sp.putInt(k, v)
                is String -> sp.putString(k, v)
            }
        }
        sp.commit()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        edit = EditText(activity).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
        activity.setContentView(edit)
        val info = EditorInfo()
        val ic = edit.onCreateInputConnection(info)
        service = Robolectric.buildService(TestService::class.java).create().get()
        service.ic = ic
        service.info = info
        root = service.onCreateInputView()
        layoutRoot()
        kv = find(root, KeyboardView::class.java)!!
        service.onStartInputView(info, false)
        // Wait for the background dictionary load.
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            idle()
            if (kv.predictor?.isReady == true) break
            Thread.sleep(20)
        }
        assertTrue("dictionary loaded", kv.predictor?.isReady == true)
        layoutRoot()
    }

    private val screenH get() = (891 * root.resources.displayMetrics.density).toInt()

    private fun layoutRoot() {
        val w = (411 * root.resources.displayMetrics.density).toInt()
        // Same constraint the IME window applies: at most the screen height.
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun <T : View> find(v: View, cls: Class<T>): T? {
        if (cls.isInstance(v)) return cls.cast(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), cls)?.let { return it }
        return null
    }

    private fun findByDesc(v: View, desc: String): View? {
        if (v.contentDescription == desc) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findByDesc(v.getChildAt(i), desc)?.let { return it }
        return null
    }

    /** Mirrors the framework telling the IME about cursor changes. */
    private fun sync() {
        service.onUpdateSelection(-1, -1, edit.selectionStart, edit.selectionEnd, -1, -1)
        idle()
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent {
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(t, t, action, x, y, 0)
    }

    private fun tapAt(x: Float, y: Float) {
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, x, y))
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_UP, x, y))
        idle()
        sync()
    }

    private fun tap(code: Int, fx: Float = 0f) {
        val r = kv.keyRect(code)!!
        tapAt(r.centerX() + fx * r.width(), r.centerY())
    }

    private fun type(s: String) = s.forEach { tap(if (it == ' ') Codes.SPACE else it.lowercaseChar().code) }

    private fun shot(name: String, settle: Boolean = true) {
        if (settle) idle(60) // let the key-press preview fade like it does on a device
        layoutRoot()
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun keyboardWrapsItsContentInsteadOfFillingTheScreen() {
        for (prefs in listOf(emptyMap(), mapOf<String, Any>("number_row" to true, "height_scale" to 130))) {
            start(prefs)
            assertTrue("height ${root.height} of $screenH", root.height < screenH / 2)
            tap(Codes.EMOJI)
            layoutRoot()
            assertTrue("emoji height ${root.height} of $screenH", root.height < screenH / 2)
        }
    }

    @Test
    fun typesWithAutoCapAndDoubleSpacePeriod() {
        start()
        type("hello world")
        assertEquals("Hello world", edit.text.toString())
        tap(Codes.SPACE)
        tap(Codes.SPACE)
        assertEquals("Hello world. ", edit.text.toString())
        type("ok")
        assertEquals("Hello world. Ok", edit.text.toString())
        tap(Codes.DELETE)
        assertEquals("Hello world. O", edit.text.toString())
    }

    @Test
    fun adaptiveTargetTurnsBorderTouchIntoLikelyLetter() {
        start()
        type("th")
        // Touch just inside the left edge of 'r' after "th": the model expects 'e'.
        tap('r'.code, fx = -0.42f)
        assertEquals("The", edit.text.toString())
        shot("hitmap_after_th_off")
        // The same touch with adaptive targets off types 'r'.
        start(mapOf("adaptive_targets" to false))
        type("th")
        tap('r'.code, fx = -0.42f)
        assertEquals("Thr", edit.text.toString())
    }

    @Test
    fun longPressTypesSymbolAfterConfiguredDelay() {
        start(mapOf("long_press_delay" to 200))
        val r = kv.keyRect('q'.code)!!
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, r.centerX(), r.centerY()))
        idle(250)
        shot("long_press_popup", settle = false)
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_UP, r.centerX(), r.centerY()))
        idle()
        assertEquals("1", edit.text.toString())
    }

    @Test
    fun spaceBarSeekMovesCursor() {
        start()
        type("hello world")
        sync()
        assertEquals(11, edit.selectionStart)
        val r = kv.keyRect(Codes.SPACE)!!
        val d = root.resources.displayMetrics.density
        val step = (30f - 50 * 0.24f) * d
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, r.centerX(), r.centerY()))
        var x = r.centerX() - 20 * d
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, x, r.centerY()))
        x -= step * 3 + 2
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, x, r.centerY()))
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_UP, x, r.centerY()))
        idle()
        assertEquals(8, edit.selectionStart)
        assertEquals("Hello world", edit.text.toString())
    }

    @Test
    fun backspaceSwipeDeletesWords() {
        start()
        type("one two three")
        sync()
        val r = kv.keyRect(Codes.DELETE)!!
        val d = root.resources.displayMetrics.density
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, r.centerX(), r.centerY()))
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, r.centerX() - 20 * d, r.centerY()))
        idle()
        sync()
        assertEquals("three", edit.text.substring(edit.selectionStart, edit.selectionEnd))
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_UP, r.centerX() - 20 * d, r.centerY()))
        idle()
        assertEquals("One two ", edit.text.toString())
    }

    @Test
    fun emojiAndClipboardPanels() {
        start()
        tap(Codes.EMOJI)
        assertNotNull(find(root, EmojiPanel::class.java))
        shot("emoji_panel")
        tap(Codes.EMOJI) // keyboard is hidden under the panel; close via toolbar instead
        findByDesc(root, "Emoji")!!.performClick()
        idle()
        val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("t", "Meet at 7pm near the station"))
        idle()
        findByDesc(root, "Clipboard")!!.performClick()
        idle()
        assertNotNull(find(root, ClipboardPanel::class.java))
        shot("clipboard_panel")
    }

    @Test
    fun screenshots() {
        for (theme in listOf("light", "dark", "amoled", "ocean", "lavender", "forest")) {
            start(mapOf("theme" to theme))
            shot("theme_$theme")
        }
        start(mapOf("theme" to "amoled", "number_row" to true))
        shot("number_row_amoled")
        start(mapOf("theme" to "dark", "show_hit_map" to true))
        type("th")
        shot("hitmap_after_th")
        start(mapOf("theme" to "light", "one_handed" to "right"))
        shot("one_handed")
        start(mapOf("theme" to "dark"))
        tap(Codes.SYMBOLS)
        shot("symbols")
        // A key mid-press: sunk, brightened, with its glass preview bubble.
        start(mapOf("theme" to "ocean"))
        val g = kv.keyRect('g'.code)!!
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, g.centerX(), g.centerY()))
        idle(50)
        shot("pressed_key", settle = false)
        kv.dispatchTouchEvent(event(MotionEvent.ACTION_UP, g.centerX(), g.centerY()))
        idle(300)
        for (theme in listOf("sunset", "rose")) {
            start(mapOf("theme" to theme))
            shot("theme_$theme")
        }
    }
}
