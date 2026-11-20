package dev.fluidkey.settings

import android.view.HapticFeedbackConstants
import dev.fluidkey.keyboard.Codes
import dev.fluidkey.keyboard.KeyboardView
import dev.fluidkey.keyboard.LayoutKind
import dev.fluidkey.keyboard.Layouts
import dev.fluidkey.keyboard.ShiftState
import dev.fluidkey.touch.NextCharPredictor

/**
 * A tiny text engine so the settings screen's live preview keyboard can be typed on: the real
 * KeyboardView, resolver and predictor, writing into an in-app string instead of another app.
 */
class PreviewTyping(private val onChange: (String) -> Unit) : KeyboardView.Listener {
    var view: KeyboardView? = null
    var numberRow = false
    var text = ""
        private set

    private fun set(t: String) {
        text = t.takeLast(160)
        onChange(text)
        updateAutoCap()
        view?.onContextChanged()
    }

    fun clear() = set("")

    fun updateAutoCap() {
        val v = view ?: return
        if (v.shiftState == ShiftState.LOCKED || v.layout.kind != LayoutKind.LETTERS) return
        val t = text.trimEnd(' ')
        val start = t.isEmpty() || t.endsWith('.') || t.endsWith('!') || t.endsWith('?')
        v.shiftState = if (start && (text.isEmpty() || text.endsWith(' '))) ShiftState.ON else ShiftState.OFF
    }

    override fun onText(text: String, fromLetter: Boolean) {
        set(this.text + text)
    }

    override fun onKeyCode(code: Int) {
        val v = view ?: return
        when (code) {
            Codes.SHIFT -> v.shiftState = when (v.shiftState) {
                ShiftState.OFF -> ShiftState.ON
                ShiftState.ON -> ShiftState.LOCKED
                ShiftState.LOCKED -> ShiftState.OFF
            }
            Codes.DELETE -> set(text.dropLast(1))
            Codes.DELETE_WORD -> set(text.trimEnd().dropLastWhile { it.isLetterOrDigit() })
            Codes.SPACE -> {
                v.resolverCommitWord()
                set("$text ")
            }
            Codes.ENTER -> set("")
            Codes.SYMBOLS -> v.layout = Layouts.symbols()
            Codes.SYMBOLS2 -> v.layout = Layouts.symbols2()
            Codes.ALPHA -> {
                v.layout = Layouts.letters(numberRow)
                updateAutoCap()
            }
            Codes.EMOJI -> set("$text🙂")
        }
    }

    override fun onCursorMove(delta: Int) {}
    override fun onDeleteSwipe(words: Int) {}

    override fun onDeleteSwipeEnd(words: Int) {
        var t = text
        repeat(words) { t = t.trimEnd().dropLastWhile { !it.isWhitespace() } }
        set(t)
    }

    override fun onSpaceLongPress() {}

    override fun currentPrefix(): String = NextCharPredictor.normalize(text.takeLastWhile { it.isLetter() })

    override fun feedback(code: Int) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
