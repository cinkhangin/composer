package com.naulian.composer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naulian.composer.IElementType
import com.naulian.composer.Node

@Composable
fun ImageBlock(modifier: Modifier = Modifier, node: Node) {

}

@Preview
@Composable
private fun ImageBlockPreview() {
    MaterialTheme {
        val node = Node(
            type = IElementType.IMAGE,
            literal = "https://picsum.photos/id/67/300/200",
        )
        ImageBlock(
            modifier = Modifier.padding(16.dp),
            node = node
        )
    }
}