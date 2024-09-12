package com.naulian.composer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.naulian.composer.CPSNode


data class Component(
    val fontFamily: FontFamily,
    val textStyle: TextStyle = TextStyle(fontFamily = fontFamily),
    val text: @Composable (
        node: CPSNode,
        style: TextStyle,
        onClickLink: (String) -> Unit
    ) -> Unit,
    val paragraph: @Composable (CPSNode, onClickLink: ((String) -> Unit)?) -> Unit,
    val header: @Composable (CPSNode) -> Unit,
    val quote: @Composable (CPSNode) -> Unit,
    val codeBlock: @Composable (CPSNode) -> Unit,
    val image: @Composable (CPSNode) -> Unit,
    val youtube: @Composable (CPSNode) -> Unit,
    val video: @Composable (CPSNode) -> Unit,
    val divider: @Composable (CPSNode) -> Unit,
    val table: @Composable (CPSNode) -> Unit,
    val elements: @Composable (CPSNode) -> Unit,
)


fun components(
    fontFamily: FontFamily = FontFamily.Default,
    textStyle: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    text: @Composable (
        node: CPSNode, style: TextStyle,
        onClickLink: ((String) -> Unit)?
    ) -> Unit = { node, style, onClickLink ->
        TextComponent(node = node, style = style, onClickLink = onClickLink)
    },
    paragraph: @Composable (
        node: CPSNode, onClickLink: ((String) -> Unit)?
    ) -> Unit = { node, onClickLink ->
        text(node, textStyle, onClickLink)
    },
    header: @Composable (CPSNode) -> Unit = { node ->
        HeaderComponent(node = node, textStyle) { content, style -> text(content, style) {} }
    },
    quote: @Composable (CPSNode) -> Unit = { node ->
        QuoteComponent(node = node) { content ->
            text(content, textStyle) {}
        } //update universal link click
    },
    codeBlock: @Composable (CPSNode) -> Unit = { node ->
        CodeComponent(node = node, textStyle = textStyle)
    },
    image: @Composable (CPSNode) -> Unit = { node ->
        ImageComponent(node = node)
    },
    youtube: @Composable (CPSNode) -> Unit = { },
    video: @Composable (CPSNode) -> Unit = { },
    divider: @Composable (CPSNode) -> Unit = { node ->
        DividerComponent(node = node)
    },
    table: @Composable (CPSNode) -> Unit = { node ->
        TableComponent(node = node) { textNode -> text(textNode, textStyle) {} }
    },
    elements: @Composable (CPSNode) -> Unit = { node ->
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
