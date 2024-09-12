package com.naulian.composer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naulian.composer.IElementType
import com.naulian.composer.ComposerNode
import com.naulian.modify.table.Table

@Composable
fun TableComponent(
    node: ComposerNode,
    textContent: @Composable (ComposerNode) -> Unit
) {
    val data = node.getTableData()
    Table(data = data) { child ->
        when (child.type) {
            IElementType.PARAGRAPH -> {
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    textContent(child)
                }
            }
        }
    }
}