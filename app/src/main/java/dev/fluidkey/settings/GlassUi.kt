@file:OptIn(ExperimentalMaterial3Api::class)

package dev.fluidkey.settings

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fluidkey.theme.GlassRenderer
import dev.fluidkey.theme.KeyboardTheme
import dev.fluidkey.theme.Themes
import kotlin.math.roundToInt

/** Material 3 theme for the app: wallpaper colours on Android 12+, FluidKey blue otherwise. */
@Composable
fun FluidKeyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = Color(0xFF9DB8FF), onPrimary = Color(0xFF0B1020), primaryContainer = Color(0xFF2B3A6E),
            onPrimaryContainer = Color(0xFFDCE4FF), secondary = Color(0xFFB9A6FF),
            background = Color(0xFF0D1119), surface = Color(0xFF0D1119),
        )
        else -> lightColorScheme(
            primary = Color(0xFF1A73E8), onPrimary = Color.White, primaryContainer = Color(0xFFD6E3FF),
            onPrimaryContainer = Color(0xFF001A41), secondary = Color(0xFF5146E5),
            background = Color(0xFFF1F4FA), surface = Color(0xFFF1F4FA),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** The keyboard theme the app's own backdrop uses (follows system, wallpaper-tinted on 12+). */
@Composable
fun appBackdropTheme(): KeyboardTheme {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    return remember(dark) { Themes.resolve(ctx, if (Build.VERSION.SDK_INT >= 31) "dynamic" else "system") }
}

/** Paints the aurora backdrop of [theme] behind the content (cached per size). */
fun Modifier.auroraBackdrop(theme: KeyboardTheme, density: Float): Modifier = drawWithCache {
    val w = size.width.roundToInt().coerceAtLeast(1)
    val h = size.height.roundToInt().coerceAtLeast(1)
    val bmp = GlassRenderer(density).apply { this.theme = theme }.backdropBitmap(w, h).asImageBitmap()
    onDrawBehind { drawImage(bmp) }
}

/** Frosted glass card: translucent fill, top sheen and a light hairline edge. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.64f))
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (dark) 0.09f else 0.5f), 0.45f to Color.Transparent))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (dark) 0.26f else 0.95f), Color.White.copy(alpha = if (dark) 0.04f else 0.3f)),
                ),
                shape,
            )
            .padding(vertical = 10.dp),
        content = content,
    )
}

@Composable
fun SectionHeader(icon: Int, title: String, subtitle: String? = null) {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, state: MutableState<Boolean>, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { state.value = !state.value }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            val alpha = if (enabled) 1f else 0.45f
            Text(title, fontSize = 15.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
        Switch(checked = state.value, onCheckedChange = { state.value = it }, enabled = enabled)
    }
}

@Composable
fun SliderRow(
    title: String,
    subtitle: String? = null,
    state: MutableState<Int>,
    range: IntRange,
    step: Int,
    enabled: Boolean = true,
    format: (Int) -> String = { it.toString() },
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f))
                if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                format(state.value),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Slider(
            value = state.value.toFloat(),
            onValueChange = { v -> state.value = (((v - range.first) / step).roundToInt() * step + range.first).coerceIn(range) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            enabled = enabled,
        )
    }
}

@Composable
fun SegmentedRow(title: String, state: MutableState<String>, options: List<Pair<String, String>>) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, fontSize = 15.5.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.size(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = state.value == value,
                    onClick = { state.value = value },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
fun ActionRow(title: String, subtitle: String? = null, icon: Int? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (icon != null) Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** A mini keyboard rendered with the real glass renderer, for the theme picker. */
@Composable
fun ThemeSwatch(id: String, selected: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val dark = isSystemInDarkTheme()
    val theme = remember(id, dark) { Themes.resolve(ctx, id) }
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "swatch")
    val shape = RoundedCornerShape(18.dp)
    val bitmap = remember(theme) { swatchBitmap(theme, density) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = Themes.label(id),
            modifier = Modifier
                .padding(4.dp)
                .scale(scale)
                .size(width = 104.dp, height = 74.dp)
                .clip(shape)
                .border(
                    if (selected) 2.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape,
                )
                .clickable(role = Role.RadioButton, onClick = onClick),
        )
        Text(
            Themes.label(id),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun swatchBitmap(theme: KeyboardTheme, density: Float): Bitmap {
    val w = (104 * density).toInt()
    val h = (74 * density).toInt()
    val r = GlassRenderer(density).apply { this.theme = theme }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    r.drawBackdrop(c, w, h)
    val pad = 10 * density
    val gap = 6 * density
    val kh = (h - 2 * pad - gap) / 2
    val kw = (w - 2 * pad - 2 * gap) / 3
    val radius = 7 * density
    for (row in 0..1) for (col in 0..2) {
        val left = pad + col * (kw + gap)
        val top = pad + row * (kh + gap)
        val rect = RectF(left, top, left + kw, top + kh)
        val kind = if (row == 1 && col == 2) GlassRenderer.Kind.ACCENT else GlassRenderer.Kind.GLASS
        r.drawShadow(c, rect, radius, kind)
        r.drawGlass(c, rect, radius, kind)
    }
    return bmp
}
