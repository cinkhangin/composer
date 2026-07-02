package composer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ScreenEyeDropper
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A full HSV color picker: a saturation/value square, a hue slider, an alpha
 * slider, a hex field, a preview, and quick swatches. [color] is ARGB packed
 * into a Long (0xAARRGGBB); [onColorChange] receives the same format.
 *
 * HSV is kept as local state so hue/saturation don't get lost at the extremes
 * (e.g. dragging value to black). External changes (swatch/hex) re-sync it.
 */
/**
 * A design-theme token offered in color pickers: [value] is the [composer.model.ThemeColorRef]
 * reference Long, [resolved] its current ARGB under the active theme.
 */
data class ThemeSwatch(val name: String, val value: Long, val resolved: Long)

/** Active-theme swatches for [ColorPicker]s; provided by the editor screen. */
val LocalThemeSwatches = compositionLocalOf<List<ThemeSwatch>> { emptyList() }

/** [value]'s displayable ARGB: theme-token references resolve via [LocalThemeSwatches]. */
@Composable
fun resolvePickerColor(value: Long): Long =
    LocalThemeSwatches.current.firstOrNull { it.value == value }?.resolved ?: value

@Composable
fun ColorPicker(color: Long, showThemeSwatches: Boolean = true, onColorChange: (Long) -> Unit) {
    var h by remember { mutableStateOf(0f) }
    var s by remember { mutableStateOf(0f) }
    var v by remember { mutableStateOf(0f) }
    var a by remember { mutableStateOf(1f) }
    var lastShown by remember { mutableStateOf<Long?>(null) }

    // [color] may be a theme-token reference — the HSV controls and preview work
    // on its resolved ARGB. Editing any control emits a plain literal (a token
    // stays a token only while untouched); resync also fires when the ACTIVE
    // THEME changes the resolved value under an unchanged token.
    val themeSwatches = if (showThemeSwatches) LocalThemeSwatches.current else emptyList()
    val display = resolvePickerColor(color)

    if (display != lastShown) {
        val hsva = argbToHsva(display)
        h = hsva.h; s = hsva.s; v = hsva.v; a = hsva.a
        lastShown = display
    }

    fun emit() {
        val c = hsvaToArgb(h, s, v, a)
        lastShown = c
        onColorChange(c)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorPreview(display, Modifier.size(34.dp))
            HexField(display, Modifier.weight(1f), onColorChange)
            // Sample any pixel on screen (EyeDropper API, Chromium-only). The
            // sampled RGB keeps the color's current alpha, like Figma.
            if (ScreenEyeDropper.supported) {
                val scope = rememberCoroutineScope()
                EyeDropperButton {
                    scope.launch {
                        ScreenEyeDropper.pick()?.let { rgb ->
                            onColorChange((color and 0xFF000000L) or (rgb and 0x00FFFFFFL))
                        }
                    }
                }
            }
        }
        SVSquare(h, s, v) { ns, nv -> s = ns; v = nv; emit() }
        HueSlider(h) { h = it; emit() }
        AlphaSlider(a, h, s, v) { a = it; emit() }
        if (themeSwatches.isNotEmpty()) ThemeSwatchRow(themeSwatches, selected = color, onPick = onColorChange)
        PresetSwatches(onColorChange)
    }
}

/**
 * The design theme's tokens as picks. Picking one stores the token REFERENCE —
 * the color follows the theme (and codegen emits `MaterialTheme.colorScheme.<token>`).
 */
@Composable
private fun ThemeSwatchRow(swatches: List<ThemeSwatch>, selected: Long, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText("Theme", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
            swatches.firstOrNull { it.value == selected }?.let {
                BasicText(it.name, style = TextStyle(color = Tk.accent, fontSize = 11.sp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (sw in swatches) {
                val sel = sw.value == selected
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(Tk.rXs))
                        .background(Color(sw.resolved))
                        .border(if (sel) 2.dp else 1.dp, if (sel) Tk.accent else Tk.borderStrong, RoundedCornerShape(Tk.rXs))
                        .clickable { onPick(sw.value) },
                )
            }
        }
    }
}

/** 34dp square button (matches [ColorPreview]) that launches the screen eyedropper. */
@Composable
private fun EyeDropperButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) Tk.elevated else Tk.panelAlt)
            .border(1.dp, if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(6.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon("colorize", Modifier.size(16.dp), tint = if (hovered) Tk.textPrimary else Tk.textSecondary)
    }
}

// --- saturation/value square ----------------------------------------------

@Composable
private fun SVSquare(h: Float, s: Float, v: Float, onChange: (Float, Float) -> Unit) {
    val hue = hueColor(h)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { svApply(it, size, onChange) },
                    onDrag = { change, _ -> change.consume(); svApply(change.position, size, onChange) },
                )
            }
            .pointerInput(Unit) { detectTapGestures { svApply(it, size, onChange) } },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hue)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = (s * size.width).coerceIn(0f, size.width)
        val cy = ((1f - v) * size.height).coerceIn(0f, size.height)
        drawCircle(Color.Black, radius = 9f, center = Offset(cx, cy), style = Stroke(width = 3f))
        drawCircle(Color.White, radius = 9f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
    }
}

private fun svApply(pos: Offset, sz: IntSize, onChange: (Float, Float) -> Unit) {
    if (sz.width == 0 || sz.height == 0) return
    onChange((pos.x / sz.width).coerceIn(0f, 1f), (1f - pos.y / sz.height).coerceIn(0f, 1f))
}

// --- hue slider ------------------------------------------------------------

private val hueStops = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

@Composable
private fun HueSlider(h: Float, onChange: (Float) -> Unit) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { hueApply(it, size, onChange) },
                    onDrag = { change, _ -> change.consume(); hueApply(change.position, size, onChange) },
                )
            }
            .pointerInput(Unit) { detectTapGestures { hueApply(it, size, onChange) } },
    ) {
        drawRect(Brush.horizontalGradient(hueStops))
        drawThumb((h / 360f) * size.width, size.height)
    }
}

private fun hueApply(pos: Offset, sz: IntSize, onChange: (Float) -> Unit) {
    if (sz.width == 0) return
    onChange((pos.x / sz.width).coerceIn(0f, 1f) * 360f)
}

// --- alpha slider ----------------------------------------------------------

@Composable
private fun AlphaSlider(a: Float, h: Float, s: Float, v: Float, onChange: (Float) -> Unit) {
    val (r, g, b) = hsvToRgb(h, s, v)
    val opaque = Color(r, g, b)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { alphaApply(it, size, onChange) },
                    onDrag = { change, _ -> change.consume(); alphaApply(change.position, size, onChange) },
                )
            }
            .pointerInput(Unit) { detectTapGestures { alphaApply(it, size, onChange) } },
    ) {
        checkerboard(6f)
        drawRect(Brush.horizontalGradient(listOf(opaque.copy(alpha = 0f), opaque)))
        drawThumb(a * size.width, size.height)
    }
}

private fun alphaApply(pos: Offset, sz: IntSize, onChange: (Float) -> Unit) {
    if (sz.width == 0) return
    onChange((pos.x / sz.width).coerceIn(0f, 1f))
}

// --- preview, hex, swatches ------------------------------------------------

@Composable
private fun ColorPreview(color: Long, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Tk.borderStrong, RoundedCornerShape(6.dp))) {
        checkerboard(6f)
        drawRect(Color(color))
    }
}

@Composable
private fun HexField(color: Long, modifier: Modifier, onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf(hex8(color)) }
    var last by remember { mutableStateOf(color) }
    if (color != last) { text = hex8(color); last = color }
    Field(
        value = text,
        isError = parseHex(text) == null,
        onValueChange = { v ->
            val cleaned = v.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(8)
            text = cleaned
            parseHex(cleaned)?.let { last = it; onChange(it) }
        },
        label = "hex (AARRGGBB)",
        modifier = modifier,
    )
}

@Composable
private fun PresetSwatches(onPick: (Long) -> Unit) {
    val swatches = listOf(
        0xFFFFFFFF, 0xFF000000, 0xFFE0E0E0, 0xFF9E9E9E,
        0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5,
        0xFF2196F3, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50,
        0xFFFFC107, 0xFFFF9800,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (c in swatches) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(Tk.rXs))
                    .background(Color(c))
                    .border(1.dp, Tk.borderStrong, RoundedCornerShape(Tk.rXs))
                    .clickable { onPick(c) },
            )
        }
    }
}

private fun DrawScope.drawThumb(x: Float, h: Float) {
    val w = 5f
    val left = (x - w / 2f).coerceIn(0f, size.width - w)
    drawRoundRect(Color.White, Offset(left, -1f), Size(w, h + 2f), CornerRadius(2.5f))
    drawRoundRect(Color(0x66000000), Offset(left, -1f), Size(w, h + 2f), CornerRadius(2.5f), style = Stroke(1f))
}

private fun DrawScope.checkerboard(cell: Float) {
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (yi in 0 until rows) for (xi in 0 until cols) {
        val light = (xi + yi) % 2 == 0
        drawRect(
            if (light) Color(0xFFFFFFFF) else Color(0xFFCBCBCB),
            topLeft = Offset(xi * cell, yi * cell),
            size = Size(cell, cell),
        )
    }
}

// --- color math ------------------------------------------------------------

private data class Hsva(val h: Float, val s: Float, val v: Float, val a: Float)

private fun hueColor(h: Float): Color {
    val (r, g, b) = hsvToRgb(h, 1f, 1f)
    return Color(r, g, b)
}

private fun rgbToHsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val s = if (max <= 0f) 0f else d / max
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d).mod(6f))
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    return Triple((h + 360f).mod(360f), s, max)
}

private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Float, Float, Float> {
    val c = v * s
    val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
    val m = v - c
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Triple(r + m, g + m, b + m)
}

private fun argbToHsva(argb: Long): Hsva {
    val a = ((argb shr 24) and 0xFFL).toInt() / 255f
    val r = ((argb shr 16) and 0xFFL).toInt() / 255f
    val g = ((argb shr 8) and 0xFFL).toInt() / 255f
    val b = (argb and 0xFFL).toInt() / 255f
    val (h, s, v) = rgbToHsv(r, g, b)
    return Hsva(h, s, v, a)
}

private fun hsvaToArgb(h: Float, s: Float, v: Float, a: Float): Long {
    val (r, g, b) = hsvToRgb(h, s, v)
    fun ch(f: Float) = (f * 255f).roundToInt().coerceIn(0, 255).toLong()
    return (ch(a) shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

private fun hex8(color: Long): String = color.toString(16).uppercase().padStart(8, '0')

private fun parseHex(text: String): Long? = when (text.length) {
    6 -> ("FF$text").toLongOrNull(16)
    8 -> text.toLongOrNull(16)
    else -> null
}
