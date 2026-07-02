package composer.model

import composer.model.ModifierSpec.Alpha
import composer.model.ModifierSpec.Background
import composer.model.ModifierSpec.Border
import composer.model.ModifierSpec.Clip
import composer.model.ModifierSpec.Padding
import composer.model.ModifierSpec.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class ModifierOrderTest {

    private val pad = Padding(16)
    private val bg = Background(0xFFFF0000)
    private val border = Border(1, 0xFF000000)
    private val clip = Clip(8)
    private val alpha = Alpha(0.5f)

    @Test
    fun fill_inserts_before_padding_so_it_is_not_a_margin() {
        // padding(16).background(...) reads as a margin; fill must wrap the box.
        assertEquals(listOf(bg, pad), listOf<ModifierSpec>(pad).insertOrdered(bg))
    }

    @Test
    fun stroke_inserts_after_fill_but_before_padding() {
        assertEquals(listOf(bg, border, pad), listOf<ModifierSpec>(bg, pad).insertOrdered(border))
    }

    @Test
    fun corner_radius_inserts_before_fill_so_the_fill_is_clipped() {
        assertEquals(listOf(clip, bg, pad), listOf<ModifierSpec>(bg, pad).insertOrdered(clip))
    }

    @Test
    fun opacity_inserts_first() {
        assertEquals(listOf(alpha, clip, bg), listOf<ModifierSpec>(clip, bg).insertOrdered(alpha))
    }

    @Test
    fun full_appearance_stack_builds_canonical_chain() {
        var mods = listOf<ModifierSpec>(pad, Size(100, 40))
        mods = mods.insertOrdered(bg)
        mods = mods.insertOrdered(clip)
        mods = mods.insertOrdered(border)
        mods = mods.insertOrdered(alpha)
        assertEquals(listOf(alpha, clip, bg, border, pad, Size(100, 40)), mods)
    }

    @Test
    fun layout_modifiers_still_append() {
        assertEquals(listOf(bg, pad), listOf<ModifierSpec>(bg).insertOrdered(pad))
    }

    @Test
    fun empty_chain_appends() {
        assertEquals(listOf<ModifierSpec>(bg), emptyList<ModifierSpec>().insertOrdered(bg))
    }
}
