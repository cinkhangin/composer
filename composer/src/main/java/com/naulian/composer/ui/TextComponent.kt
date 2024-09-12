package com.naulian.composer.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.naulian.composer.CPSNode

@Composable
fun TextComponent(
    modifier: Modifier = Modifier,
    node: CPSNode,
    style: TextStyle,
    onClickLink: ((String) -> Unit)? = null
) {
    when {
        node.children.isEmpty() && node.literal.isBlank() -> {}
        else -> {
            val paragraphContent = buildContentPair(node)
            onClickLink?.let { onClick ->
                ClickableText(
                    text = paragraphContent.annotatedString,
                    style = style,
                ) { offset ->
                    paragraphContent.annotatedString.getStringAnnotations(
                        start = offset,
                        end = offset
                    ).firstOrNull()?.let {
                        paragraphContent.linkMap[it.tag]?.let(onClick)
                    }
                }
            } ?: run {
                Text(
                    text = paragraphContent.annotatedString,
                    style = style
                )
            }
        }
    }
}