package com.naulian.composer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.naulian.composer.ComposerNode


data class Component(
    val fontFamily: FontFamily,
    val textStyle: TextStyle = TextStyle(fontFamily = fontFamily),
    val text: @Composable (
        node: ComposerNode,
        style: TextStyle,
        onClickLink: ((String) -> Unit)?
    ) -> Unit,
    val paragraph: @Composable (ComposerNode, onClickLink: ((String) -> Unit)?) -> Unit,
    val header: @Composable (ComposerNode) -> Unit,
    val quote: @Composable (ComposerNode) -> Unit,
    val codeBlock: @Composable (ComposerNode) -> Unit,
    val image: @Composable (ComposerNode) -> Unit,
    val youtube: @Composable (ComposerNode) -> Unit,
    val video: @Composable (ComposerNode) -> Unit,
    val divider: @Composable (ComposerNode) -> Unit,
    val table: @Composable (ComposerNode) -> Unit,
    val elements: @Composable (ComposerNode) -> Unit,
)


fun components(
    fontFamily: FontFamily = FontFamily.Default,
    textStyle: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    text: @Composable (
        node: ComposerNode, style: TextStyle, onClickLink: ((String) -> Unit)?
    ) -> Unit = { node, style, onClickLink ->
        TextComponent(node = node, style = style, onClickLink = onClickLink)
    },
    paragraph: @Composable (
        node: ComposerNode,
        onClickLink: ((String) -> Unit)?
    ) -> Unit = { node, onClickLink ->
        text(node, textStyle, onClickLink)
    },
    header: @Composable (ComposerNode) -> Unit = { node ->
        HeaderComponent(node = node, textStyle) { content, style -> text(content, style) {} }
    },
    quote: @Composable (ComposerNode) -> Unit = { node ->
        QuoteComponent(node = node) { content ->
            text(content, textStyle) {}
        } //update universal link click
    },
    codeBlock: @Composable (ComposerNode) -> Unit = { node ->
        CodeComponent(node = node, textStyle = textStyle)
    },
    image: @Composable (ComposerNode) -> Unit = { node ->
        ImageComponent(node = node)
    },
    youtube: @Composable (ComposerNode) -> Unit = { },
    video: @Composable (ComposerNode) -> Unit = { },
    divider: @Composable (ComposerNode) -> Unit = { node ->
        DividerComponent(node = node)
    },
    table: @Composable (ComposerNode) -> Unit = { node ->
        TableComponent(node = node) { textNode -> text(textNode, textStyle) {} }
    },
    elements: @Composable (ComposerNode) -> Unit = { node ->
        ElementComponent(node = node) { textNode -> text(textNode, textStyle) {} }
    }
) = Component(
    fontFamily = fontFamily,
    text = text,
    paragraph = paragraph,
    header = header,
    quote = quote,
    codeBlock = codeBlock,
    image = image,
    youtube = youtube,
    video = video,
    divider = divider,
    table = table,
    elements = elements
)
