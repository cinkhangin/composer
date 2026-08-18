package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import composer.model.ModifierSpec
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

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AddChipsRow(existing: List<ModifierSpec>, canWeight: Boolean, alignSeed: ModifierSpec.Align?, onAdd: (ModifierSpec) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        AddChip("padding") { onAdd(Padding(8)) }
        AddChip("size") { onAdd(Size(100, 40)) }
        AddChip("width") { onAdd(ModifierSpec.Width(100)) }
        AddChip("height") { onAdd(ModifierSpec.Height(48)) }
        AddChip("offset") { onAdd(Offset(0, 0)) }
        AddChip("background") { onAdd(Background(0xFF2196F3)) }
        if (canWeight && existing.none { it is Weight }) AddChip("weight") { onAdd(Weight(1f)) }
        AddChip("aspectRatio") { onAdd(AspectRatio(1, 1)) }
        AddChip("clip") { onAdd(Clip(12)) }
        AddChip("opacity") { onAdd(ModifierSpec.Alpha(0.5f)) }
        AddChip("border") { onAdd(ModifierSpec.Border(1, 0xFF000000)) }
        AddChip("dropShadow") { onAdd(ModifierSpec.DropShadow()) }
        AddChip("innerShadow") { onAdd(ModifierSpec.InnerShadow()) }
        AddChip("rotate") { onAdd(ModifierSpec.Rotate(45f)) }
        AddChip("scale") { onAdd(ModifierSpec.Scale(1.5f, 1.5f)) }
        AddChip("zIndex") { onAdd(ModifierSpec.ZIndex(1f)) }
        AddChip("blur") { onAdd(ModifierSpec.Blur(8)) }
        if (alignSeed != null) AddChip("align") { onAdd(alignSeed) }
        AddChip("fillW") { onAdd(FillMaxWidth()) }
        AddChip("fillH") { onAdd(FillMaxHeight()) }
        AddChip("fillSize") { onAdd(FillMaxSize()) }
    }
}
