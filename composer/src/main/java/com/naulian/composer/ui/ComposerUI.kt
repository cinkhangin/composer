package com.naulian.composer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naulian.anhance.millisOfNow
import com.naulian.composer.IElementType
import com.naulian.composer.CPSNode
import com.naulian.composer.Parser
import com.naulian.composer.CPS_SAMPLE
import com.naulian.glow_compose.hexToColor

@Composable
fun ComposerUI(
    modifier: Modifier = Modifier,
    source: String,
    onClickLink: ((String) -> Unit)? = null,
    components: Component = components(),
    contentSpacing: Dp = 12.dp
) {
    var node by remember {
        mutableStateOf(CPSNode())
    }

    LaunchedEffect(key1 = source) {
        node = Parser(source).parse()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(contentSpacing)
    ) {
        HandleNode(node, components, onClickLink)
    }
}

@Composable
fun HandleNode(node: CPSNode, components: Component, onClickLink: ((String) -> Unit)?) {
    node.children.forEach {
        when (it.type) {
            IElementType.PARAGRAPH -> components.paragraph(it, onClickLink)
            IElementType.H1, IElementType.H2, IElementType.H3, IElementType.H4, IElementType.H5, IElementType.H6 -> {
                components.header(it)
            }

            IElementType.DIVIDER -> components.divider(it)
            IElementType.CODE -> components.codeBlock(it)
            IElementType.QUOTATION -> components.quote(it)
            IElementType.IMAGE -> components.image(it)
            IElementType.YOUTUBE -> components.youtube(it)
            IElementType.VIDEO -> components.video(it)
            IElementType.ELEMENT -> components.elements(it)

            IElementType.TABLE -> components.table(it)
            else -> components.text(it, components.textStyle) {}
        }
    }
}


@Composable
fun AnnotatedString.Builder.handleText(
    node: CPSNode,
    linkMap: Map<String, String> = emptyMap()
): Map<String, String> {

    val map = hashMapOf<String, String>()
    linkMap.forEach { map[it.key] = it.value }

    if (node.children.isEmpty()) {
        when (node.type) {
            IElementType.LINK, IElementType.HYPER_LINK -> {
                pushStyle(style = SpanStyle(color = Color.Blue))
                val tag = "link$millisOfNow"
                val (hyper, link) = node.getHyperLink()
                pushStringAnnotation(tag, "link")
                append(hyper.ifEmpty { link })
                pop() // annotation
                pop() //style
                map[tag] = link
                return map
            }

            else -> append(node.literal)
        }
        return map
    }

    val spanStyle = when (node.type) {
        IElementType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        IElementType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        IElementType.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        IElementType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        IElementType.COLORED -> SpanStyle(color = node.literal.hexToColor())
        else -> SpanStyle()
    }

    pushStyle(style = spanStyle)
    node.children.forEach { child ->
        val innerMap = handleText(node = child, map)
        innerMap.forEach { map[it.key] = it.value }
    }
    pop()
    return map
}


@Composable
fun buildContentPair(node: CPSNode): ParagraphContent {
    var linkMap = mapOf<String, String>()
    val annotatedString = buildAnnotatedString {
        linkMap = handleText(node = node)
    }
    return ParagraphContent(annotatedString, linkMap)
}

data class ParagraphContent(
    val annotatedString: AnnotatedString = emptyAnnotatedString,
    val linkMap: Map<String, String> = emptyMap()
)

@Preview(heightDp = 1500)
@Composable
private fun ComposerUIPreview() {
    MaterialTheme {
        Surface(color = Color.White) {
            ComposerUI(
                modifier = Modifier.padding(16.dp),
                source = CPS_SAMPLE
            )
        }
    }
}