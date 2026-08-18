package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.GradientDirection
import composer.model.ModifierSpec
import composer.model.PaddingMode
import composer.model.ModifierSpec.AspectRatio
import composer.model.ModifierSpec.Clip
import composer.model.ModifierSpec.Background
import composer.model.ModifierSpec.FillMaxHeight
import composer.model.ModifierSpec.FillMaxSize
import composer.model.ModifierSpec.FillMaxWidth
import composer.model.ModifierSpec.Offset
import composer.model.ModifierSpec.Padding
import composer.model.ModifierSpec.Size
import composer.model.ModifierSpec.Weight
import composer.ui.ColorField
import composer.ui.Tk

@Composable
internal fun ModifierParams(spec: ModifierSpec, onChange: (ModifierSpec) -> Unit) {
    when (spec) {
        is ModifierSpec.External -> BasicText(
            if (spec.preview.isEmpty()) {
                "Source modifier: ${spec.expression}. Previewed as Modifier."
            } else {
                "Source modifier: ${spec.expression}. ${spec.preview.size} static call(s) previewed."
            },
            style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
        )
        is ModifierSpec.ScaffoldPadding -> BasicText(
            "Runtime padding from Scaffold (${spec.parameter}).",
            style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
        )
        is Padding -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaddingModeRow(spec.mode) { m -> onChange(spec.copy(mode = m)) }
            when (spec.mode) {
                PaddingMode.All -> IntField("all (dp)", spec.all, Modifier.fillMaxWidth()) { onChange(spec.copy(all = it)) }
                PaddingMode.Symmetric -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntField("horizontal", spec.horizontal, Modifier.weight(1f)) { onChange(spec.copy(horizontal = it)) }
                    IntField("vertical", spec.vertical, Modifier.weight(1f)) { onChange(spec.copy(vertical = it)) }
                }
                PaddingMode.Sides -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IntField("start", spec.start, Modifier.weight(1f)) { onChange(spec.copy(start = it)) }
                    IntField("top", spec.top, Modifier.weight(1f)) { onChange(spec.copy(top = it)) }
                    IntField("end", spec.end, Modifier.weight(1f)) { onChange(spec.copy(end = it)) }
                    IntField("bottom", spec.bottom, Modifier.weight(1f)) { onChange(spec.copy(bottom = it)) }
                }
            }
        }

        is Size -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField("width", spec.width, Modifier.weight(1f)) { onChange(Size(it, spec.height)) }
            IntField("height", spec.height, Modifier.weight(1f)) { onChange(Size(spec.width, it)) }
        }

        is ModifierSpec.Width -> IntField("width", spec.width, Modifier.fillMaxWidth()) { onChange(ModifierSpec.Width(it)) }

        is ModifierSpec.Height -> IntField("height", spec.height, Modifier.fillMaxWidth()) { onChange(ModifierSpec.Height(it)) }

        is Offset -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField("x", spec.x, Modifier.weight(1f), allowNegative = true) { onChange(Offset(it, spec.y)) }
            IntField("y", spec.y, Modifier.weight(1f), allowNegative = true) { onChange(Offset(spec.x, it)) }
        }

        is Weight -> FloatField("weight", spec.value, Modifier.fillMaxWidth()) { onChange(Weight(it)) }

        is Clip -> CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(Clip(c, u)) }

        is ModifierSpec.Alpha -> FloatField("opacity (0–1)", spec.value, Modifier.fillMaxWidth()) { onChange(ModifierSpec.Alpha(it.coerceIn(0f, 1f))) }

        is ModifierSpec.Border -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField("width (dp)", spec.width, Modifier.fillMaxWidth()) { onChange(spec.copy(width = it)) }
            ColorField(spec.color) { onChange(spec.copy(color = it)) }
            CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(spec.copy(corner = c, cornerUnit = u)) }
        }

        is AspectRatio -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            IntField("width", spec.width, Modifier.weight(1f)) { onChange(AspectRatio(it, spec.height)) }
            BasicText(":", style = TextStyle(color = Tk.textMuted, fontSize = 14.sp), modifier = Modifier.padding(bottom = 9.dp))
            IntField("height", spec.height, Modifier.weight(1f)) { onChange(AspectRatio(spec.width, it)) }
        }

        is Background -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val gradient = spec.gradientStops().isNotEmpty()
            SegRow(listOf(false to "solid", true to "gradient"), gradient) { grad ->
                // Toggling on seeds two stops from the solid color (no visual jump);
                // toggling off keeps the solid color and drops the stop list.
                onChange(spec.copy(colors = if (grad) listOf(spec.color, spec.color) else emptyList()))
            }
            if (gradient) {
                GradientStopsEditor(spec.colors) { onChange(spec.copy(colors = it)) }
                SegRow(
                    GradientDirection.entries.map { it to it.name.lowercase() },
                    spec.direction,
                ) { onChange(spec.copy(direction = it)) }
            } else {
                ColorField(spec.color) { onChange(spec.copy(color = it)) }
            }
            CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(spec.copy(corner = c, cornerUnit = u)) }
        }

        is ModifierSpec.DropShadow -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorField(spec.color) { onChange(spec.copy(color = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("blur (dp)", spec.radius, Modifier.weight(1f)) { onChange(spec.copy(radius = it)) }
                IntField("spread", spec.spread, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(spread = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("x", spec.offsetX, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(offsetX = it)) }
                IntField("y", spec.offsetY, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(offsetY = it)) }
            }
            CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(spec.copy(corner = c, cornerUnit = u)) }
        }

        is ModifierSpec.InnerShadow -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorField(spec.color) { onChange(spec.copy(color = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("blur (dp)", spec.radius, Modifier.weight(1f)) { onChange(spec.copy(radius = it)) }
                IntField("spread", spec.spread, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(spread = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("x", spec.offsetX, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(offsetX = it)) }
                IntField("y", spec.offsetY, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(offsetY = it)) }
            }
            CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(spec.copy(corner = c, cornerUnit = u)) }
        }

        is ModifierSpec.Align -> when {
            spec.box != null -> EnumDropdown("align", spec.box!!, composer.model.BoxAlignment.entries, itemLabel = { it.name }) { onChange(ModifierSpec.Align(box = it)) }
            spec.vertical != null -> EnumDropdown("align (vertical)", spec.vertical!!, composer.model.VAlignment.entries, itemLabel = { it.name }) { onChange(ModifierSpec.Align(vertical = it)) }
            else -> EnumDropdown("align (horizontal)", spec.horizontal ?: composer.model.HAlignment.Center, composer.model.HAlignment.entries, itemLabel = { it.name }) { onChange(ModifierSpec.Align(horizontal = it)) }
        }

        is ModifierSpec.ZIndex -> FloatField("z-index", spec.value, Modifier.fillMaxWidth()) { onChange(ModifierSpec.ZIndex(it)) }

        is ModifierSpec.Blur -> IntField("radius (dp)", spec.radius, Modifier.fillMaxWidth()) { onChange(ModifierSpec.Blur(it)) }

        is ModifierSpec.Rotate -> FloatField("degrees", spec.degrees, Modifier.fillMaxWidth()) { onChange(ModifierSpec.Rotate(it)) }

        is ModifierSpec.Scale -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatField("x", spec.x, Modifier.weight(1f)) { onChange(spec.copy(x = it)) }
            FloatField("y", spec.y, Modifier.weight(1f)) { onChange(spec.copy(y = it)) }
        }

        is FillMaxWidth -> FloatField("fraction (0–1)", spec.fraction, Modifier.fillMaxWidth()) { onChange(FillMaxWidth(it.coerceIn(0f, 1f))) }
        is FillMaxHeight -> FloatField("fraction (0–1)", spec.fraction, Modifier.fillMaxWidth()) { onChange(FillMaxHeight(it.coerceIn(0f, 1f))) }
        is FillMaxSize -> FloatField("fraction (0–1)", spec.fraction, Modifier.fillMaxWidth()) { onChange(FillMaxSize(it.coerceIn(0f, 1f))) }
    }
}
