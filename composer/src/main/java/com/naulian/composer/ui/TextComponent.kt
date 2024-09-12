package com.naulian.composer.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.naulian.composer.ComposerNode

@Composable
fun TextComponent(
    modifier: Modifier = Modifier,
    node: ComposerNode,
    style: TextStyle,
    onClickLink: ((String) -> Unit)? = null
) {
    when {
        node.children.isEmpty() && node.literal.isBlank() -> {}
        else -> {
            val paragraphContent = buildAnnotatedString { HandleText(node = node, onClickLink) }
            onClickLink?.let {
                BasicText(
                    modifier = modifier,
                    text = paragraphContent,
                    style = style,
                )
            } ?: run {
                Text(
                    modifier = modifier,
                    text = paragraphContent,
                    style = style
                )
            }
        }
    }
}