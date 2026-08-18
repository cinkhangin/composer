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

    @Test fun boxCanBecomeAnyLayoutWithoutLosingStructure() {
        val child = Node.Text("t", "hi")
        val box = Node.Box(
            id = "b",
            children = listOf(child),
            contentAlignment = BoxAlignment.BottomEnd,
            modifier = listOf(Padding(12)),
        )

        val row = box.convertLayout(LayoutKind.Row) as Node.Row
        assertEquals("b", row.id)
        assertEquals(listOf(child), row.children)
        assertEquals(listOf(Padding(12)), row.modifier)
        assertEquals(HArrangement.End, row.horizontalArrangement)
        assertEquals(VAlignment.Bottom, row.verticalAlignment)

        val column = box.convertLayout(LayoutKind.Column) as Node.Column
        assertEquals(VArrangement.Bottom, column.verticalArrangement)
        assertEquals(HAlignment.End, column.horizontalAlignment)
    }

    @Test fun rowAndColumnCanBecomeBoxWithAlignmentIntent() {
        val fromRow = Node.Row(
            id = "r",
            horizontalArrangement = HArrangement.Center,
            verticalAlignment = VAlignment.Bottom,
        ).convertLayout(LayoutKind.Box) as Node.Box
        assertEquals(BoxAlignment.BottomCenter, fromRow.contentAlignment)

        val fromColumn = Node.Column(
            id = "c",
            verticalArrangement = VArrangement.Center,
            horizontalAlignment = HAlignment.End,
        ).convertLayout(LayoutKind.Box) as Node.Box
        assertEquals(BoxAlignment.CenterEnd, fromColumn.contentAlignment)
    }
}
