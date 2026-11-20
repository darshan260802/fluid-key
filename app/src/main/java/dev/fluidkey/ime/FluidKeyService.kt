package dev.fluidkey.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import dev.fluidkey.R
import dev.fluidkey.clipboard.ClipboardPanel
import dev.fluidkey.clipboard.ClipboardStore
import dev.fluidkey.emoji.EmojiPanel
import dev.fluidkey.keyboard.Codes
import dev.fluidkey.keyboard.KeyboardView
import dev.fluidkey.keyboard.LayoutKind
import dev.fluidkey.keyboard.Layouts
import dev.fluidkey.keyboard.PopupOverlay
import dev.fluidkey.keyboard.ShiftState
import dev.fluidkey.settings.SettingsActivity
import dev.fluidkey.theme.KeyboardTheme
import dev.fluidkey.theme.Themes
import dev.fluidkey.touch.AdaptiveTouchResolver
import dev.fluidkey.touch.NextCharPredictor
import java.io.File

/** Open only so instrumentation-free tests can supply a real InputConnection. */
open class FluidKeyService : InputMethodService(), KeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sp: SharedPreferences
    private lateinit var data: SharedPreferences
    private lateinit var prefs: Prefs
    private lateinit var clipStore: ClipboardStore

    @Volatile
    private var predictor = NextCharPredictor()
    private val resolver = AdaptiveTouchResolver()
    private val userWords = HashMap<String, Int>()
    private var userWordsDirty = false

    private var theme: KeyboardTheme = Themes.DARK
    private var keyboardView: KeyboardView? = null
    private var overlay: PopupOverlay? = null
    private var body: FrameLayout? = null
    private var keyboardRow: View? = null
    private var emojiPanel: EmojiPanel? = null
    private var clipPanel: ClipboardPanel? = null
    private var clipChip: TextView? = null
    private var needsRebuild = false

    private var kind = LayoutKind.LETTERS
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L
    private var lastSpaceTime = 0L
    private val prefix = StringBuilder()
    private var selStart = -1
    private var selEnd = -1
    private var swipeAnchor = -1
    private var swipeText: CharSequence = ""
    private var clipChipText: String? = null

    private var hapticsOn = true
    private var soundOn = false
    private var vibrator: Vibrator? = null
    private var audio: AudioManager? = null

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate() {
        super.onCreate()
        sp = PreferenceManager.getDefaultSharedPreferences(this)
        data = getSharedPreferences(DATA_PREFS, Context.MODE_PRIVATE)
        prefs = Prefs(sp)
        clipStore = ClipboardStore(data)
        sp.registerOnSharedPreferenceChangeListener(this)
        resolver.deserialize(data.getString(KEY_OFFSETS, null))
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
        loadPredictor()
    }

    override fun onDestroy() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
        sp.unregisterOnSharedPreferenceChangeListener(this)
        persist()
        super.onDestroy()
    }

    private fun loadPredictor() {
        val p = NextCharPredictor()
        predictor = p
        Thread {
            try {
                assets.open("words_en.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val sp = line.indexOf(' ')
                        if (sp > 0) p.addFrequency(line.substring(0, sp), line.substring(sp + 1).toLongOrNull() ?: 1L)
                    }
                }
                val f = File(filesDir, USER_WORDS)
                if (f.exists()) {
                    val loaded = HashMap<String, Int>()
                    f.forEachLine { line ->
                        val parts = line.split(' ')
                        val n = parts.getOrNull(1)?.toIntOrNull()
                        if (parts.size == 2 && n != null) loaded[parts[0]] = n
                    }
                    loaded.forEach { (w, n) -> p.learn(w, n) }
                    synchronized(userWords) { userWords.putAll(loaded) }
                }
            } catch (_: Exception) {
            }
            p.markReady()
            keyboardView?.post {
                keyboardView?.predictor = predictor
                keyboardView?.onContextChanged()
            }
        }.apply { name = "FluidKey-dict"; priority = Thread.NORM_PRIORITY - 1 }.start()
    }

    private fun persist() {
        data.edit().putString(KEY_OFFSETS, resolver.serialize()).apply()
        if (userWordsDirty) {
            userWordsDirty = false
            val snapshot = synchronized(userWords) { userWords.toMap() }
            try {
                File(filesDir, USER_WORDS).writeText(snapshot.entries.joinToString("\n") { "${it.key} ${it.value}" })
            } catch (_: Exception) {
            }
        }
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onSharedPreferenceChanged(s: SharedPreferences?, key: String?) {
        if (key == "reset_learning_token") {
            resolver.reset()
            synchronized(userWords) { userWords.clear() }
            File(filesDir, USER_WORDS).delete()
            data.edit().remove(KEY_OFFSETS).apply()
            loadPredictor()
            return
        }
        needsRebuild = true
        if (isInputViewShown) rebuild()
    }

    private fun rebuild() {
        needsRebuild = false
        setInputView(onCreateInputView())
        applyLayout(kind)
        updateEnterAction()
        updateAutoCap()
    }

    // ---------------------------------------------------------------- views

    override fun onCreateInputView(): View {
        theme = Themes.resolve(this, prefs.theme)
        hapticsOn = prefs.haptics
        soundOn = prefs.sound
        resolver.enabled = prefs.adaptive
        resolver.strength = prefs.adaptiveStrength
        resolver.learning = prefs.adaptiveLearning
        emojiPanel = null
        clipPanel = null

        val root = FrameLayout(this).apply { setBackgroundColor(theme.background) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(buildToolbar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46f)))

        val kv = KeyboardView(this, resolver).apply {
            listener = this@FluidKeyService
            predictor = this@FluidKeyService.predictor
            theme = this@FluidKeyService.theme
            keyBorders = prefs.keyBorders
            showHints = prefs.showHints
            showPreview = prefs.keyPreview
            showHitMap = prefs.showHitMap
            backspaceSwipe = prefs.backspaceSwipe
            longPressDelay = prefs.longPressDelay
            heightScale = prefs.heightScale
            // Sensitivity 0..100 -> 30dp..6dp of finger travel per character.
            seekStepPx = Ui.dp(this@FluidKeyService, 30f - prefs.spaceSeekSensitivity * 0.24f).toFloat()
        }
        keyboardView = kv

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        when (prefs.oneHanded) {
            "left" -> {
                row.addView(kv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.84f))
                row.addView(buildOneHandSide("right"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.16f))
            }
            "right" -> {
                row.addView(buildOneHandSide("left"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.16f))
                row.addView(kv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.84f))
            }
            else -> row.addView(kv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        keyboardRow = row
        val b = FrameLayout(this)
        b.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body = b
        column.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val ov = PopupOverlay(this).apply { theme = this@FluidKeyService.theme }
        root.addView(ov, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        kv.overlay = ov
        overlay = ov

        // Edge-to-edge (targetSdk 35): keep keys clear of the navigation bar, paint behind it.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            column.setPadding(nav.left, 0, nav.right, nav.bottom)
            insets
        }
        return root
    }

    private fun buildToolbar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val size = Ui.dp(this, 46f)
        bar.addView(Ui.iconButton(this, theme, R.drawable.ic_clipboard, getString(R.string.clipboard)) { toggleClipboard() }, LinearLayout.LayoutParams(size, size))
        bar.addView(Ui.iconButton(this, theme, R.drawable.ic_emoji, getString(R.string.emoji)) { toggleEmoji() }, LinearLayout.LayoutParams(size, size))
        val chip = TextView(this).apply {
            setTextColor(theme.text)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(this@FluidKeyService, 12f)
            setPadding(p, 0, p, 0)
            compoundDrawablePadding = Ui.dp(this@FluidKeyService, 6f)
            val icon = ContextCompat.getDrawable(this@FluidKeyService, R.drawable.ic_clipboard)!!.mutate()
            icon.setTint(theme.accent)
            val s = Ui.dp(this@FluidKeyService, 16f)
            icon.setBounds(0, 0, s, s)
            setCompoundDrawablesRelative(icon, null, null, null)
            background = Ui.rounded(theme.key, Ui.dp(this@FluidKeyService, 16f).toFloat())
            visibility = View.INVISIBLE
            setOnClickListener {
                clipChipText?.let { commit(it) }
                hideClipChip()
            }
        }
        clipChip = chip
        val chipWrap = FrameLayout(this)
        chipWrap.addView(chip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 32f), Gravity.CENTER))
        bar.addView(chipWrap, LinearLayout.LayoutParams(0, size, 1f))
        bar.addView(Ui.iconButton(this, theme, R.drawable.ic_one_hand, getString(R.string.one_handed)) {
            val next = when (prefs.oneHanded) { "off" -> "right"; else -> "off" }
            sp.edit().putString("one_handed", next).apply()
        }, LinearLayout.LayoutParams(size, size))
        bar.addView(Ui.iconButton(this, theme, R.drawable.ic_tune, getString(R.string.settings)) {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, LinearLayout.LayoutParams(size, size))
        bar.addView(Ui.iconButton(this, theme, R.drawable.ic_keyboard_hide, getString(R.string.hide)) {
            requestHideSelf(0)
        }, LinearLayout.LayoutParams(size, size))
        return bar
    }

    private fun buildOneHandSide(moveTo: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val s = Ui.dp(this, 48f)
        col.addView(Ui.iconButton(this, theme, R.drawable.ic_fullscreen, getString(R.string.full_width)) {
            sp.edit().putString("one_handed", "off").apply()
        }, LinearLayout.LayoutParams(s, s))
        col.addView(Ui.iconButton(this, theme, if (moveTo == "left") R.drawable.ic_chevron_left else R.drawable.ic_chevron_right, getString(R.string.switch_side)) {
            sp.edit().putString("one_handed", moveTo).apply()
        }, LinearLayout.LayoutParams(s, s))
        return col
    }

    private fun showPanel(panel: View) {
        val b = body ?: return
        closePanels()
        keyboardView?.cancelAll()
        // Panels take exactly the keyboard's height; MATCH_PARENT inside the wrap_content
        // IME frame would let a scrolling grid grow to the whole screen.
        val h = keyboardRow?.height?.takeIf { it > 0 } ?: keyboardView?.measuredHeight ?: Ui.dp(this, 260f)
        keyboardRow?.visibility = View.INVISIBLE
        b.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
    }

    private fun closePanels() {
        emojiPanel?.let { (it.parent as? ViewGroup)?.removeView(it) }
        clipPanel?.let { (it.parent as? ViewGroup)?.removeView(it) }
        emojiPanel = null
        clipPanel = null
        keyboardRow?.visibility = View.VISIBLE
    }

    private fun toggleEmoji() {
        if (emojiPanel != null) { closePanels(); return }
        val panel = EmojiPanel(this, theme, data,
            onEmoji = { commit(it) },
            onDelete = { feedback(Codes.DELETE); handleDelete() },
            onClose = { closePanels() })
        showPanel(panel)
        emojiPanel = panel
    }

    private fun toggleClipboard() {
        if (clipPanel != null) { closePanels(); return }
        val panel = ClipboardPanel(this, theme, clipStore,
            onPaste = { commit(it); closePanels() },
            onClose = { closePanels() })
        showPanel(panel)
        clipPanel = panel
        hideClipChip()
    }

    // ---------------------------------------------------------------- clipboard

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!prefs.clipboardHistory) return@OnPrimaryClipChangedListener
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return@OnPrimaryClipChangedListener
            if (clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return@OnPrimaryClipChangedListener
            val text = clip.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            if (text.isNullOrEmpty() || text.length > 10_000) return@OnPrimaryClipChangedListener
            clipStore.add(text)
            clipPanel?.refresh()
            showClipChip(text)
        } catch (_: Exception) {
        }
    }

    private fun showClipChip(text: String) {
        val chip = clipChip ?: return
        clipChipText = text
        chip.text = text.replace('\n', ' ')
        chip.visibility = View.VISIBLE
        chip.removeCallbacks(hideChipRunnable)
        chip.postDelayed(hideChipRunnable, 90_000)
    }

    private val hideChipRunnable = Runnable { hideClipChip() }

    private fun hideClipChip() {
        clipChipText = null
        clipChip?.visibility = View.INVISIBLE
    }

    // ---------------------------------------------------------------- input session

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (needsRebuild) rebuild()
        closePanels()
        selStart = info.initialSelStart
        selEnd = info.initialSelEnd
        kind = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> LayoutKind.NUMBER
            InputType.TYPE_CLASS_PHONE -> LayoutKind.PHONE
            else -> LayoutKind.LETTERS
        }
        applyLayout(kind)
        updateEnterAction()
        shift = ShiftState.OFF
        refreshPrefix()
        updateAutoCap()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardView?.cancelAll()
        persist()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        if (swipeAnchor < 0) {
            refreshPrefix()
            updateAutoCap()
        }
    }

    private fun applyLayout(k: LayoutKind) {
        kind = k
        keyboardView?.layout = when (k) {
            LayoutKind.LETTERS -> Layouts.letters(prefs.numberRow)
            LayoutKind.SYMBOLS -> Layouts.symbols()
            LayoutKind.SYMBOLS2 -> Layouts.symbols2()
            LayoutKind.NUMBER -> Layouts.number()
            LayoutKind.PHONE -> Layouts.phone()
        }
        keyboardView?.shiftState = shift
    }

    private fun updateEnterAction() {
        val info = currentInputEditorInfo ?: return
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        keyboardView?.enterAction = if (noAction) EditorInfo.IME_ACTION_NONE else action
    }

    private fun learningAllowed(): Boolean {
        val info = currentInputEditorInfo ?: return false
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        if (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) return false
        return cls == InputType.TYPE_CLASS_TEXT
    }

    private fun refreshPrefix() {
        prefix.setLength(0)
        val before = currentInputConnection?.getTextBeforeCursor(48, 0) ?: return
        var i = before.length
        while (i > 0 && before[i - 1].let { it in 'a'..'z' || it in 'A'..'Z' }) i--
        prefix.append(before.subSequence(i, before.length).toString().lowercase())
        keyboardView?.onContextChanged()
    }

    private fun updateAutoCap() {
        if (shift == ShiftState.LOCKED || kind != LayoutKind.LETTERS) return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val caps = prefs.autoCap && ic.getCursorCapsMode(info.inputType) != 0
        shift = if (caps) ShiftState.ON else ShiftState.OFF
        keyboardView?.shiftState = shift
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    /** The word in progress just ended (space, punctuation, enter): learn from it. */
    private fun endWord() {
        val word = prefix.toString()
        if (word.length >= 2 && learningAllowed()) {
            resolver.commitWord()
            if (predictor.isReady) {
                predictor.learn(word)
                synchronized(userWords) { userWords[word] = (userWords[word] ?: 0) + 1 }
                userWordsDirty = true
            }
        }
        prefix.setLength(0)
    }

    // ---------------------------------------------------------------- KeyboardView.Listener

    override fun currentPrefix(): String = prefix.toString()

    override fun onText(text: String, fromLetter: Boolean) {
        val ic = currentInputConnection ?: return
        if (fromLetter) {
            ic.commitText(text, 1)
            prefix.append(text.lowercase())
            if (shift == ShiftState.ON) {
                shift = ShiftState.OFF
                keyboardView?.shiftState = shift
            }
        } else {
            val endsWord = text.length == 1 && !text[0].isLetter() && text != "'"
            if (endsWord) endWord()
            ic.commitText(text, 1)
            if (!endsWord) refreshPrefix()
        }
        keyboardView?.onContextChanged()
    }

    override fun onKeyCode(code: Int) {
        when (code) {
            Codes.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                val quick = now - lastShiftTap < 320
                shift = when (shift) {
                    ShiftState.OFF -> ShiftState.ON
                    ShiftState.ON -> if (quick) ShiftState.LOCKED else ShiftState.OFF
                    ShiftState.LOCKED -> ShiftState.OFF
                }
                lastShiftTap = now
                keyboardView?.shiftState = shift
            }
            Codes.SYMBOLS -> applyLayout(LayoutKind.SYMBOLS)
            Codes.SYMBOLS2 -> applyLayout(LayoutKind.SYMBOLS2)
            Codes.ALPHA -> { applyLayout(LayoutKind.LETTERS); updateAutoCap() }
            Codes.DELETE -> handleDelete()
            Codes.DELETE_WORD -> deleteWord()
            Codes.ENTER -> handleEnter()
            Codes.SPACE -> handleSpace()
            Codes.EMOJI -> toggleEmoji()
        }
    }

    private fun handleSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (prefs.doubleSpacePeriod && now - lastSpaceTime < 450 && kind == LayoutKind.LETTERS) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastSpaceTime = 0
                return
            }
        }
        endWord()
        ic.commitText(" ", 1)
        lastSpaceTime = now
    }

    private fun handleDelete() {
        val ic = currentInputConnection ?: return
        if (selStart != selEnd && selStart >= 0) {
            ic.commitText("", 1)
        } else {
            val before = ic.getTextBeforeCursor(32, 0)
            if (before.isNullOrEmpty()) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            } else {
                ic.deleteSurroundingText(lastGraphemeLength(before), 0)
            }
        }
        if (prefix.isNotEmpty()) prefix.setLength(prefix.length - 1)
        keyboardView?.onContextChanged()
    }

    private fun lastGraphemeLength(s: CharSequence): Int {
        val bi = BreakIterator.getCharacterInstance()
        val str = s.toString()
        bi.setText(str)
        val prev = bi.preceding(str.length)
        return if (prev == BreakIterator.DONE) 1 else (str.length - prev).coerceAtLeast(1)
    }

    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)
        if (before.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        val start = wordStartBack(before, 1)
        ic.deleteSurroundingText((before.length - start).coerceAtLeast(1), 0)
        prefix.setLength(0)
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        endWord()
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendKeyChar('\n')
        }
    }

    override fun onCursorMove(delta: Int) {
        val ic = currentInputConnection ?: return
        if (selEnd < 0) {
            sendDownUpKeyEvents(if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            return
        }
        val target = if (delta > 0) {
            val after = ic.getTextAfterCursor(delta, 0) ?: return
            if (after.isEmpty()) return
            selEnd + after.length
        } else {
            if (selStart <= 0) return
            (selStart + delta).coerceAtLeast(0)
        }
        ic.setSelection(target, target)
        selStart = target
        selEnd = target
        tick()
    }

    override fun onDeleteSwipe(words: Int) {
        val ic = currentInputConnection ?: return
        if (swipeAnchor < 0) {
            if (selEnd < 0) return
            swipeAnchor = selEnd
            swipeText = ic.getTextBeforeCursor(2000, 0) ?: ""
        }
        val start = if (words == 0) swipeText.length else wordStartBack(swipeText, words)
        val from = swipeAnchor - (swipeText.length - start)
        ic.setSelection(from, swipeAnchor)
        tick()
    }

    override fun onDeleteSwipeEnd(words: Int) {
        val ic = currentInputConnection
        if (swipeAnchor >= 0 && ic != null) {
            if (words > 0) ic.commitText("", 1) else ic.setSelection(swipeAnchor, swipeAnchor)
        }
        swipeAnchor = -1
        swipeText = ""
        refreshPrefix()
        updateAutoCap()
    }

    override fun onSpaceLongPress() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun feedback(code: Int) {
        if (hapticsOn) tick()
        if (soundOn) {
            val fx = when (code) {
                Codes.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                Codes.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                Codes.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(fx, -1f)
        }
    }

    private fun tick() {
        if (!hapticsOn) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            else v.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    companion object {
        const val DATA_PREFS = "fluidkey_data"
        private const val KEY_OFFSETS = "touch_offsets"
        private const val USER_WORDS = "user_words.txt"

        /** Index where the [words]-th word before the end of [text] starts. */
        fun wordStartBack(text: CharSequence, words: Int): Int {
            var i = text.length
            repeat(words) {
                while (i > 0 && text[i - 1].isWhitespace()) i--
                if (i > 0 && !text[i - 1].isLetterOrDigit()) {
                    i--
                } else {
                    while (i > 0 && (text[i - 1].isLetterOrDigit() || text[i - 1] == '\'')) i--
                }
            }
            return i
        }
    }
}
