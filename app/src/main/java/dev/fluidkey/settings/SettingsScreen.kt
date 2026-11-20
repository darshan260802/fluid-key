package dev.fluidkey.settings

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.fluidkey.R
import dev.fluidkey.ime.Dictionary
import dev.fluidkey.keyboard.KeyboardView
import dev.fluidkey.keyboard.Layouts
import dev.fluidkey.theme.BackdropDrawable
import dev.fluidkey.theme.GlassRenderer
import dev.fluidkey.theme.Themes
import dev.fluidkey.touch.AdaptiveTouchResolver
import kotlinx.coroutines.launch

enum class SetupState { DISABLED, ENABLED, READY }

@Composable
fun SettingsScreen(
    store: PrefsStore,
    setup: SetupState,
    version: String,
    onEnable: () -> Unit,
    onSelect: () -> Unit,
    onResetLearning: () -> Unit,
    onClearClipboard: () -> Unit,
    onOpenDocs: () -> Unit,
) {
    val density = LocalDensity.current.density
    val backdrop = appBackdropTheme()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    Box(Modifier.fillMaxSize().auroraBackdrop(backdrop, density)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header()
            SetupCard(setup, onEnable, onSelect)
            PreviewCard(store)
            ThemeCard(store)

            val adaptive = store.bool("adaptive_targets", true)
            GlassCard {
                SectionHeader(R.drawable.ic_touch, "Fluid touch", "Prediction-driven key targets")
                SwitchRow("Adaptive hit targets", "Likely next letters quietly grow; unlikely ones shrink", adaptive)
                SliderRow("Prediction strength", "0 is a classic keyboard", store.int("adaptive_strength", 50), 0..100, 5, adaptive.value) { "$it%" }
                SwitchRow("Learn my typing", "Adapts to where you aim and the words you use", store.bool("adaptive_learning", true), adaptive.value)
                SwitchRow("Show hit map", "Visualise the moving key edges (debug)", store.bool("show_hit_map", false))
                ActionRow("Reset learned data", "Forget aim offsets and learned words") {
                    onResetLearning()
                    toast("Learned typing data reset")
                }
            }
            GlassCard {
                SectionHeader(R.drawable.ic_palette, "Look & feel")
                SwitchRow("Key borders", "Glass key shapes. Off for a borderless look", store.bool("key_borders", true))
                SliderRow("Keyboard height", null, store.int("height_scale", 100), 75..130, 5) { "$it%" }
                SegmentedRow("One-handed mode", store.string("one_handed", "off"), listOf("off" to "Off", "left" to "Left", "right" to "Right"))
                SwitchRow("Number row", null, store.bool("number_row", false))
                SwitchRow("Long-press hints", "Symbol in the corner of each key", store.bool("show_hints", true))
                SwitchRow("Key press popup", null, store.bool("key_preview", true))
            }
            GlassCard {
                SectionHeader(R.drawable.ic_keyboard, "Typing")
                SliderRow("Long-press delay", "Hold time for symbols and accents", store.int("long_press_delay", 300), 100..800, 25) { "$it ms" }
                SwitchRow("Auto-capitalisation", null, store.bool("auto_cap", true))
                SwitchRow("Double-space period", "Tap space twice for \". \"", store.bool("double_space_period", true))
                SwitchRow("Vibrate on keypress", null, store.bool("haptics", true))
                SwitchRow("Sound on keypress", null, store.bool("sound", false))
            }
            GlassCard {
                SectionHeader(R.drawable.ic_gesture, "Gestures")
                SliderRow("Space bar cursor speed", "Hold space and slide to move the cursor", store.int("space_seek_sensitivity", 50), 0..100, 5) { "$it" }
                SwitchRow("Backspace swipe", "Swipe left from ⌫ to delete whole words", store.bool("backspace_swipe", true))
            }
            GlassCard {
                SectionHeader(R.drawable.ic_clipboard, "Clipboard")
                SwitchRow("Clipboard history", "Sensitive clips like passwords are skipped", store.bool("clipboard_history", true))
                ActionRow("Clear clipboard history", "Pinned items are kept") {
                    onClearClipboard()
                    toast("Clipboard history cleared")
                }
            }
            GlassCard {
                SectionHeader(R.drawable.ic_info, "About", "FluidKey $version")
                ActionRow("How adaptive touch works", "The research behind FluidKey", R.drawable.ic_open, onOpenDocs)
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_shield), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "No internet permission. Everything FluidKey learns stays on this device.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars))
    }
}

@Composable
private fun Header() {
    Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_keyboard), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("FluidKey", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "The keyboard that knows where you meant to tap",
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupCard(setup: SetupState, onEnable: () -> Unit, onSelect: () -> Unit) {
    GlassCard {
        AnimatedContent(setup, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "setup") { state ->
            if (state == SetupState.READY) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_check_circle), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("You're all set", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("FluidKey is your active keyboard.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Get started", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Step(1, "Enable FluidKey", "Turn it on in your keyboard list", done = state != SetupState.DISABLED, active = state == SetupState.DISABLED, action = "Enable", onClick = onEnable)
                    Step(2, "Switch to FluidKey", "Pick it as your keyboard", done = false, active = state == SetupState.ENABLED, action = "Select", onClick = onSelect)
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, desc: String, done: Boolean, active: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (done || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            else Text("$n", fontWeight = FontWeight.Bold, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (active) Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 18.dp)) { Text(action) }
    }
}

@Composable
private fun PreviewCard(store: PrefsStore) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    var typed by remember { mutableStateOf("") }
    val typing = remember { PreviewTyping { typed = it } }
    val renderer = remember { GlassRenderer(density) }
    val resolver = remember { AdaptiveTouchResolver() }
    val predictor = remember { Dictionary.loadAsync(ctx) { _, _ -> typing.view?.post { typing.view?.onContextChanged() } } }

    val themeId by store.string("theme", "system")
    val borders by store.bool("key_borders", true)
    val hints by store.bool("show_hints", true)
    val height by store.int("height_scale", 100)
    val numberRow by store.bool("number_row", false)
    val adaptive by store.bool("adaptive_targets", true)
    val strength by store.int("adaptive_strength", 50)
    val longPress by store.int("long_press_delay", 300)
    val hitMapPref = store.bool("show_hit_map", false)
    val theme = remember(themeId) { Themes.resolve(ctx, themeId) }

    GlassCard {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Try it", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Live preview with your settings", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilterChip(selected = hitMapPref.value, onClick = { hitMapPref.value = !hitMapPref.value }, label = { Text("Hit map") })
        }
        // The "text field": what the preview keyboard typed, with a blinking caret.
        Row(
            Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (typed.isEmpty()) "Type on the keyboard below…" else typed,
                fontSize = 16.sp,
                color = if (typed.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (typed.isNotEmpty()) Caret()
        }
        AndroidView(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .padding(bottom = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            factory = { c ->
                FrameLayout(c).apply {
                    val kv = KeyboardView(c, resolver)
                    kv.renderer = renderer
                    kv.predictor = predictor
                    kv.listener = typing
                    kv.spaceLabel = "Try me"
                    typing.view = kv
                    addView(kv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
            },
            update = { frame ->
                val kv = frame.getChildAt(0) as KeyboardView
                if (frame.tag != theme) {
                    renderer.theme = theme
                    frame.background = BackdropDrawable(renderer)
                    frame.tag = theme
                }
                kv.theme = theme
                kv.keyBorders = borders
                kv.showHints = hints
                kv.heightScale = height / 100f
                kv.longPressDelay = longPress.toLong()
                kv.showHitMap = hitMapPref.value
                resolver.enabled = adaptive
                resolver.strength = strength / 50f
                if (typing.numberRow != numberRow || kv.tag == null) {
                    typing.numberRow = numberRow
                    kv.layout = Layouts.letters(numberRow)
                    kv.tag = true
                    typing.updateAutoCap()
                }
                kv.invalidate()
            },
        )
    }
}

/** Blinking caret; only composed (and animating) while there is text. */
@Composable
private fun Caret() {
    val caret by rememberInfiniteTransition(label = "caret").animateFloat(
        1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "caretAlpha",
    )
    Box(Modifier.padding(start = 1.dp).size(2.dp, 20.dp).alpha(caret).background(MaterialTheme.colorScheme.primary))
}

@Composable
private fun ThemeCard(store: PrefsStore) {
    val theme = store.string("theme", "system")
    GlassCard {
        SectionHeader(R.drawable.ic_palette, "Theme", Themes.label(theme.value))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Themes.PICKER) { id ->
                ThemeSwatch(id, selected = theme.value == id) { theme.value = id }
            }
        }
    }
}
