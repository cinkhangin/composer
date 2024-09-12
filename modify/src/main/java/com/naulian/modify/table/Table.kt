package com.naulian.modify.table

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naulian.modify.Preview

@Composable
fun <T> Table(
    modifier: Modifier = Modifier,
    data: List<List<T>>,
    rowHeight: Dp = 40.dp,
    lineThickness: Dp = 1.dp,
    lineColor: Color = MaterialTheme.colorScheme.outline,
    borderShape: RoundedCornerShape = RoundedCornerShape(12.dp),
    cell: @Composable (T) -> Unit,
) {
    Column(
        modifier = modifier.border(
            width = lineThickness,
            color = lineColor,
            shape = borderShape
        )
    ) {
        for (i in data.indices) {
            TableRow(row = data[i], rowHeight = rowHeight) { cell ->
                TableCell(modifier = Modifier.weight(1f), item = cell) {
                    cell(it)
                }
            }
            if (i < data.lastIndex) {
                HorizontalDivider(color = lineColor)
            }
        }
    }
}

@Composable
fun <T> TableRow(
    modifier: Modifier = Modifier,
    row: List<T>,
    rowHeight: Dp = 40.dp,
    lineThickness: Dp = 1.dp,
    lineColor: Color = MaterialTheme.colorScheme.outline,
    cell: @Composable RowScope.(T) -> Unit,
) {
    Row(
        modifier
            .height(rowHeight)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in row.indices) {
            cell(row[i])
            if (i < row.lastIndex) {
                VerticalDivider(color = lineColor, thickness = lineThickness)
            }
        }
    }
}

@Composable
fun <T> TableCell(
    item: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    Box(
        modifier = modifier.height(40.dp),
        contentAlignment = Alignment.CenterStart,
        content = { content(item) }
    )
}


@Preview(showBackground = true)
@Composable
fun LazyTablePreview() {
    val data = listOf(
        listOf("Lorem", "Lorem", ""),
        listOf("Lorem", "", "Lorem"),
        listOf("Lorem", "Lorem", ""),
        listOf("Lorem", "Lorem", "")
    )

    Preview {
        Table(modifier = Modifier.padding(12.dp), data = data) {
            Text(modifier = Modifier.padding(horizontal = 10.dp), text = it)
        }
    }
}