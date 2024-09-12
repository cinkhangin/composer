package com.naulian.composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.text.isDigitsOnly
import coil.compose.AsyncImage
import com.naulian.anhance.millisOfNow
import com.naulian.composer.IElementType
import com.naulian.composer.Node
import com.naulian.composer.Parser
import com.naulian.composer.TEST_SAMPLE
import com.naulian.glow_compose.R
import com.naulian.glow_compose.hexToColor
import com.naulian.modify.table.Table

@Composable
fun ComposerUI(
    modifier: Modifier = Modifier,
    source: String,
    onClickLink: ((String) -> Unit)? = null,
    components: Component = components(),
    contentSpacing: Dp = 12.dp
) {
    var node by remember {
        mutableStateOf(Node())
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
fun HandleNode(node: Node, components: Component, onClickLink: ((String) -> Unit)?) {
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
    node: Node,
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

data class Component(
    val fontFamily: FontFamily,
    val textStyle: TextStyle = TextStyle(fontFamily = fontFamily),
    val text: @Composable (
        node: Node,
        style: TextStyle,
        onClickLink: (String) -> Unit
    ) -> Unit,
    val paragraph: @Composable (Node, onClickLink: ((String) -> Unit)?) -> Unit,
    val header: @Composable (Node) -> Unit,
    val quote: @Composable (Node) -> Unit,
    val codeBlock: @Composable (Node) -> Unit,
    val image: @Composable (Node) -> Unit,
    val youtube: @Composable (Node) -> Unit,
    val video: @Composable (Node) -> Unit,
    val divider: @Composable (Node) -> Unit,
    val table: @Composable (Node) -> Unit,
    val elements: @Composable (Node) -> Unit,
)

@Composable
fun buildContentPair(node: Node): MdxParagraphContent {
    var linkMap = mapOf<String, String>()
    val annotatedString = buildAnnotatedString {
        linkMap = handleText(node = node)
    }
    return MdxParagraphContent(annotatedString, linkMap)
}

data class MdxParagraphContent(
    val annotatedString: AnnotatedString = emptyAnnotatedString,
    val linkMap: Map<String, String> = emptyMap()
)

fun components(
    fontFamily: FontFamily = FontFamily.Default,
    textStyle: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    text: @Composable (
        node: Node,
        style: TextStyle,
        onClickLink: ((String) -> Unit)?
    ) -> Unit = { node, style, onClickLink ->
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
    },
    paragraph: @Composable (Node, onClickLink: ((String) -> Unit)?) -> Unit = { node, onClickLink ->
        text(node, textStyle, onClickLink)
    },
    header: @Composable (Node) -> Unit = { node ->
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
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = sizePair.first,
            lineHeight = sizePair.second,
        )

        text(node, style) {}
    },
    quote: @Composable (Node) -> Unit = { node ->
        ConstraintLayout(
            modifier = Modifier
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
                        IElementType.PARAGRAPH -> text(it, textStyle) {}
                        else -> {}
                    }
                }
            }
        }
    },
    codeBlock: @Composable (Node) -> Unit = { node ->
        val (lang, code) = node.getLangCodePair()
        when (lang) {
            "comment" -> {}
            else -> CodeSnippet(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    )
                    .clip(MaterialTheme.shapes.small),
                textStyle = textStyle,
                source = code,
                language = lang
            )
        }
    },
    image: @Composable (Node) -> Unit = { node ->
        var isError by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            isError = false
        }

        if (!isError) {
            AsyncImage(
                model = node.literal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small),
                contentDescription = "Atx Picture",
                contentScale = ContentScale.FillWidth,
                placeholder = painterResource(R.drawable.img_placeholder),
                onError = { isError = true },
            )
        }
    },
    youtube: @Composable (Node) -> Unit = { },
    video: @Composable (Node) -> Unit = { },
    divider: @Composable (Node) -> Unit = { node ->
        when (node.literal) {
            "" -> {}
            "br" -> Spacer(modifier = Modifier.height(1.dp))
            "line" -> HorizontalDivider()
            "dash" -> HorizontalDashDivider()
            else -> {
                if (node.literal.isDigitsOnly()) {
                    val sizeInt = node.literal.toInt()
                    Spacer(modifier = Modifier.height(sizeInt.dp))
                }
            }
        }
    },
    table: @Composable (Node) -> Unit = { node ->
        val data = node.getTableData()
        Table(data = data) { child ->
            when (child.type) {
                IElementType.PARAGRAPH -> {
                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                        text(child, textStyle) {}
                    }
                }
            }
        }
    },
    elements: @Composable (Node) -> Unit = { node ->
        Column(modifier = Modifier.fillMaxWidth()) {
            node.children.forEach { element ->
                when (element.type) {
                    IElementType.ELEMENT_UNCHECKED -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "\u2610")
                            Spacer(modifier = Modifier.width(12.dp))
                            element.children.forEach {
                                when (it.type) {
                                    IElementType.PARAGRAPH -> text(it, textStyle) {}
                                    else -> {}
                                }
                            }
                        }
                    }

                    IElementType.ELEMENT_CHECKED -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "\u2611")
                            Spacer(modifier = Modifier.width(12.dp))
                            element.children.forEach {
                                when (it.type) {
                                    IElementType.PARAGRAPH -> text(it, textStyle) {}
                                    else -> {}
                                }
                            }
                        }
                    }

                    IElementType.ELEMENT_BULLET -> {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "\u2022")
                            Spacer(modifier = Modifier.width(12.dp))
                            element.children.forEach {
                                when (it.type) {
                                    IElementType.PARAGRAPH -> text(it, textStyle) {}
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
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


@Preview(heightDp = 1500)
@Composable
private fun ComposerUIPreview() {
    MaterialTheme {
        Surface(color = Color.White) {
            ComposerUI(
                modifier = Modifier.padding(16.dp),
                source = TEST_SAMPLE
            )
        }
    }
}