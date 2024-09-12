package com.naulian.composer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.naulian.composer.ComposerNode
import com.naulian.composer.IElementType

@Composable
fun HeaderComponent(
    node: ComposerNode, textStyle: TextStyle,
    textComponent: @Composable (ComposerNode, TextStyle) -> Unit
) {
    val sizePair = when (node.type) {
        IElementType.H1 -> 36.sp to 42.sp
        IElementType.H2 -> 32.sp to 36.sp
        IElementType.H3 -> 28.sp to 32.sp
        IElementType.H4 -> 24.sp to 38.sp
        IElementType.H5 -> 20.sp to 24.sp
        IElementType.H6 -> 18.sp to 22.sp
        else -> 24.sp to 32.sp
    }
    val style = textStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = sizePair.first,
        lineHeight = sizePair.second,
    )

    textComponent(node, style)
}