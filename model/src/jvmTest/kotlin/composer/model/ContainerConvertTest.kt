package composer.model

import composer.model.ModifierSpec.Padding
import kotlin.test.Test
import kotlin.test.assertEquals

class ContainerConvertTest {

    @Test fun columnToRowMapsAxes() {
        val col = Node.Column(
            "c",
            children = listOf(Node.Text("t", "hi")),
            verticalArrangement = VArrangement.Bottom,
            horizontalAlignment = HAlignment.End,
            modifier = listOf(Padding(8)),
            spacing = 12,
        )
        val row = col.toRow()
        assertEquals("c", row.id)
        assertEquals(HArrangement.End, row.horizontalArrangement)   // Bottom → End (main axis)
        assertEquals(VAlignment.Bottom, row.verticalAlignment)      // End → Bottom (cross axis)
        assertEquals(12, row.spacing)
        assertEquals(listOf(Padding(8)), row.modifier)
        assertEquals(col.children, row.children)
    }

    @Test fun roundTripPreservesPackedIntent() {
        val col = Node.Column(
            "c",
            verticalArrangement = VArrangement.Center,
            horizontalAlignment = HAlignment.Center,
        )
        assertEquals(col, col.toRow().toColumn())
    }

    @Test fun spaceArrangementSurvivesFlip() {
        val col = Node.Column("c", verticalArrangement = VArrangement.SpaceBetween)
        assertEquals(HArrangement.SpaceBetween, col.toRow().horizontalArrangement)
    }
}
