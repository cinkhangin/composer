package composer.model

import composer.model.ModifierSpec.FillMaxHeight
import composer.model.ModifierSpec.FillMaxSize
import composer.model.ModifierSpec.FillMaxWidth
import composer.model.ModifierSpec.Padding
import composer.model.ModifierSpec.Size
import composer.model.ModifierSpec.Weight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SizingTest {

    // --- reading ------------------------------------------------------------

    @Test fun emptyIsHug() {
        val m = emptyList<ModifierSpec>()
        assertEquals(SizeMode.Hug, m.sizeMode(SizeAxis.Width, weightIsFill = false))
        assertEquals(SizeMode.Hug, m.sizeMode(SizeAxis.Height, weightIsFill = false))
    }

    @Test fun widthModifierReadsFixed() {
        val m = listOf(ModifierSpec.Width(240))
        assertEquals(SizeMode.Fixed(240), m.sizeMode(SizeAxis.Width, weightIsFill = false))
        assertEquals(SizeMode.Hug, m.sizeMode(SizeAxis.Height, weightIsFill = false))
    }

    @Test fun legacySizeReadsFixedOnBothAxes() {
        val m = listOf(Size(120, 48))
        assertEquals(SizeMode.Fixed(120), m.sizeMode(SizeAxis.Width, weightIsFill = false))
        assertEquals(SizeMode.Fixed(48), m.sizeMode(SizeAxis.Height, weightIsFill = false))
    }

    @Test fun fillMaxWidthReadsFill() {
        val m = listOf(FillMaxWidth)
        assertEquals(SizeMode.Fill, m.sizeMode(SizeAxis.Width, weightIsFill = false))
        assertEquals(SizeMode.Hug, m.sizeMode(SizeAxis.Height, weightIsFill = false))
    }

    @Test fun fillMaxSizeReadsFillOnBothAxes() {
        val m = listOf(FillMaxSize)
        assertEquals(SizeMode.Fill, m.sizeMode(SizeAxis.Width, weightIsFill = false))
        assertEquals(SizeMode.Fill, m.sizeMode(SizeAxis.Height, weightIsFill = false))
    }

    @Test fun weightIsFillOnlyOnMainAxis() {
        val m = listOf(Weight(1f))
        // In a Row, weight fills the width (main axis) but not the height.
        assertEquals(SizeMode.Fill, m.sizeMode(SizeAxis.Width, weightIsFill = true))
        assertEquals(SizeMode.Hug, m.sizeMode(SizeAxis.Height, weightIsFill = false))
    }

    // --- writing ------------------------------------------------------------

    @Test fun setFixedWidthAddsWidthModifier() {
        val next = emptyList<ModifierSpec>().withSizeMode(SizeAxis.Width, SizeMode.Fixed(200), weightIsFill = false)
        assertEquals(listOf(ModifierSpec.Width(200)), next)
    }

    @Test fun setFillWidthAddsFillMaxWidthOffMainAxis() {
        val next = emptyList<ModifierSpec>().withSizeMode(SizeAxis.Width, SizeMode.Fill, weightIsFill = false)
        assertEquals(listOf(FillMaxWidth), next)
    }

    @Test fun setFillWidthAddsWeightOnMainAxis() {
        val next = emptyList<ModifierSpec>().withSizeMode(SizeAxis.Width, SizeMode.Fill, weightIsFill = true)
        assertEquals(listOf(Weight(1f)), next)
    }

    @Test fun setHugRemovesWidthConstraint() {
        val next = listOf(ModifierSpec.Width(200), Padding(8)).withSizeMode(SizeAxis.Width, SizeMode.Hug, weightIsFill = false)
        assertEquals(listOf(Padding(8)), next)
    }

    @Test fun settingOneAxisPreservesTheOther() {
        // Start Fixed×Fixed via legacy Size, change width to Fill, height must stay Fixed(48).
        val next = listOf(Size(120, 48)).withSizeMode(SizeAxis.Width, SizeMode.Fill, weightIsFill = false)
        assertEquals(SizeMode.Fill, next.sizeMode(SizeAxis.Width, weightIsFill = false))
        assertEquals(SizeMode.Fixed(48), next.sizeMode(SizeAxis.Height, weightIsFill = false))
        assertTrue(next.none { it is Size }, "legacy Size should be decomposed away")
    }

    @Test fun switchingModesDoesNotAccumulate() {
        var m: List<ModifierSpec> = emptyList()
        m = m.withSizeMode(SizeAxis.Width, SizeMode.Fixed(100), weightIsFill = false)
        m = m.withSizeMode(SizeAxis.Width, SizeMode.Fill, weightIsFill = false)
        m = m.withSizeMode(SizeAxis.Width, SizeMode.Fixed(300), weightIsFill = false)
        assertEquals(listOf(ModifierSpec.Width(300)), m)
    }

    @Test fun fillSetOnMainAxisReplacesWeightNotDuplicates() {
        val m = listOf(Weight(1f)).withSizeMode(SizeAxis.Width, SizeMode.Fixed(80), weightIsFill = true)
        assertEquals(listOf(ModifierSpec.Width(80)), m)
    }

    @Test fun heightFillOffMainAxisUsesFillMaxHeight() {
        val next = emptyList<ModifierSpec>().withSizeMode(SizeAxis.Height, SizeMode.Fill, weightIsFill = false)
        assertEquals(listOf(FillMaxHeight), next)
    }
}
