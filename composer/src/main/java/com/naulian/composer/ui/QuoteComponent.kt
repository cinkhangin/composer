package com.naulian.composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.naulian.composer.IElementType
import com.naulian.composer.CPSNode

@Composable
fun QuoteComponent(
    modifier: Modifier = Modifier,
    node: CPSNode, textComponent: @Composable (CPSNode) -> Unit
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small
            )
            .clip(MaterialTheme.shapes.small)
    ) {
        val (accent, content) = createRefs()
        Box(
            modifier = Modifier
                .width(8.dp)
                .background(MaterialTheme.colorScheme.primary)
                .constrainAs(accent) {
                    start.linkTo(parent.start)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    height = Dimension.fillToConstraints
                }
        )
        Box(
            modifier = Modifier
                .constrainAs(content) {
                    start.linkTo(accent.end)
                    end.linkTo(parent.end)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    width = Dimension.fillToConstraints
                }
                .padding(12.dp)
        ) {
            node.children.forEach {
                when (it.type) {
                    IElementType.PARAGRAPH -> textComponent(it)
                    else -> {}
                }
            }
        }
    }
}

@Preview
@Composable
fun QuoteComponentPreview() {
    MaterialTheme {
        Surface(color = Color.LightGray) {
            QuoteComponent(
                modifier = Modifier.padding(16.dp),
                node = CPSNode(
                    type = IElementType.QUOTATION,
                    literal = "",
                    children = listOf(
                        CPSNode(
                            type = IElementType.PARAGRAPH,
                            literal = "This is a Quote - author",
                            children = emptyList()
                        )
                    )
                ),
                textComponent = {
                    Text(it.literal)
                }
            )
        }
    }
}