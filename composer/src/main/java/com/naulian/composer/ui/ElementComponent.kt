package com.naulian.composer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naulian.composer.CPSNode
import com.naulian.composer.IElementType

@Composable
fun ElementComponent(
    modifier: Modifier = Modifier,
    node: CPSNode,
    textContent: @Composable (CPSNode) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        node.children.forEach { element ->
            when (element.type) {
                IElementType.ELEMENT_UNCHECKED -> {
                    ElementText(
                        elementNode = element,
                        bullet = "\u2610",
                        textContent = textContent
                    )
                }

                IElementType.ELEMENT_CHECKED -> {
                    ElementText(
                        elementNode = element,
                        bullet = "\u2611",
                        textContent = textContent
                    )
                }

                IElementType.ELEMENT_BULLET -> {
                    ElementText(
                        elementNode = element,
                        bullet = "\u2022",
                        textContent = textContent
                    )
                }
            }
        }
    }
}

@Composable
fun ElementText(
    modifier: Modifier = Modifier,
    elementNode: CPSNode,
    bullet: String,
    textContent: @Composable (CPSNode) -> Unit
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(text = bullet)
        Spacer(modifier = Modifier.width(12.dp))
        elementNode.children.forEach {
            when (it.type) {
                IElementType.PARAGRAPH -> textContent(it)
                else -> {}
            }
        }
    }
}

@Preview
@Composable
private fun ElementTextPreview() {
    MaterialTheme {
        //ElementText(bullet = "\ud81a\uddf9")
    }
}