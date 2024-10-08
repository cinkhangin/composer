package com.naulian.composer

private val textTypes = listOf(
    IElementType.BLOCK_SYMBOL,
    IElementType.COLOR_START,
    IElementType.COLOR_END,
    IElementType.BOLD,
    IElementType.ITALIC,
    IElementType.UNDERLINE,
    IElementType.STRIKE,
    IElementType.TEXT,
    IElementType.PARAGRAPH,
    IElementType.LINK,
    IElementType.HYPER_LINK,
    IElementType.WHITESPACE,
    IElementType.NEWLINE,
    IElementType.DATETIME,
    IElementType.ESCAPE,
    IElementType.IGNORE,
    IElementType.COLORED
)

private val elementTypes = listOf(
    IElementType.ELEMENT,
    IElementType.ELEMENT_BULLET,
    IElementType.ELEMENT_UNCHECKED,
    IElementType.ELEMENT_CHECKED
)

class ASTBuilder(private val nodes: List<ComposerNode>) {

    fun buildTyped(node: ComposerNode = ComposerNode(children = nodes)): ComposerNode {
        if (node.children.isEmpty()) {
            return node
        }

        var index = 0
        val children = node.children
        val childNodes = mutableListOf<ComposerNode>()
        while (children.getOrNull(index) != null && children[index].type != IElementType.EOF) {
            val current = children[index]
            when (current.type) {
                in textTypes -> {
                    val start = index
                    while (children.getOrNull(index) != null && children[index].type in textTypes && children[index] != ComposerNode.EOF) {
                        index++
                    }

                    val subChild = children.subList(start, index)
                    val paragraphNode = ComposerNode(
                        type = IElementType.PARAGRAPH,
                        children = subChild.trimWhiteSpaces()
                    )

                    if (paragraphNode.children.isNotEmpty()) {
                        childNodes.add(paragraphNode)
                    }
                }

                in elementTypes -> {
                    val start = index
                    while (
                        children.getOrNull(index) != null
                        && (children[index].type in elementTypes
                                || children[index].type == IElementType.NEWLINE)
                        && children[index] != ComposerNode.EOF
                    ) {
                        index++
                    }
                    val subChild = children.subList(start, index)
                        .filter { it.type != IElementType.NEWLINE }
                        .map { buildTyped(it) }

                    val elementNode = ComposerNode(
                        type = IElementType.ELEMENT,
                        children = subChild.trimWhiteSpaces()
                    )

                    if (elementNode.children.isNotEmpty()) {
                        childNodes.add(elementNode)
                    }
                }

                else -> {
                    if (current.children.isNotEmpty()) {
                        val child = buildTyped(current)
                        childNodes.add(child)
                    } else childNodes.add(children[index])
                    index++
                }
            }
        }

        return node.copy(children = childNodes)
    }

    fun build(node: ComposerNode = ComposerNode(children = nodes)): ComposerNode {
        if (node.children.isEmpty()) {
            return node
        }

        var index = 0
        val children = node.children
        val childNodes = mutableListOf<ComposerNode>()
        while (children.getOrNull(index) != null && children[index].type != IElementType.EOF) {
            val current = children[index]
            when (current.type) {
                IElementType.HEADER -> {
                    index++
                    val start = index
                    while (children.getOrNull(index) != null && children[index].type != IElementType.NEWLINE && children[index] != ComposerNode.EOF) {
                        index++
                    }
                    val subChild = children.subList(start, index)

                    val nodeType = when (current.literal) {
                        "#1" -> IElementType.H1
                        "#2" -> IElementType.H2
                        "#3" -> IElementType.H3
                        "#4" -> IElementType.H4
                        "#5" -> IElementType.H5
                        "#6" -> IElementType.H6
                        else -> IElementType.TEXT
                    }
                    val h1Node = ComposerNode(
                        type = nodeType,
                        children = subChild.trimWhiteSpaces()
                    )
                    val child = build(h1Node)
                    childNodes.add(child)
                }

                IElementType.BLOCK_SYMBOL -> {
                    index++
                    val start = index
                    while (children.getOrNull(index) != null && children[index].literal != current.literal && children[index] != ComposerNode.EOF) {
                        index++
                    }
                    val subChild = children.subList(start, index)
                    index++ // skip the closing symbol

                    val nodeType = when (current.literal) {
                        "&" -> IElementType.BOLD
                        "/" -> IElementType.ITALIC
                        "_" -> IElementType.UNDERLINE
                        "~" -> IElementType.STRIKE
                        "\"" -> IElementType.QUOTATION
                        else -> IElementType.TEXT
                    }
                    val blockNode = ComposerNode(
                        type = nodeType,
                        children = subChild.trimWhiteSpaces()
                    )
                    val child = build(blockNode)
                    childNodes.add(child)
                }

                IElementType.TABLE_START -> {
                    index++
                    val start = index
                    while (children.getOrNull(index) != null && children[index].type != IElementType.TABLE_END && children[index] != ComposerNode.EOF) {
                        index++
                    }
                    val subChild = children.subList(start, index).trimWhiteSpaces()
                    index++ // skip the closing symbol

                    val tableColumns = mutableListOf<ComposerNode>()
                    var cols = mutableListOf<ComposerNode>()

                    subChild.forEach {
                        if (it.type == IElementType.NEWLINE) {
                            val colNode = ComposerNode(
                                type = IElementType.TABLE_COLOMN,
                                children = cols.trimWhiteSpaces()
                            )
                            val child = build(colNode)
                            tableColumns.add(child)
                            cols = mutableListOf()
                        } else cols.add(it)
                    }

                    if (cols.isNotEmpty()) {
                        val colNode = ComposerNode(
                            type = IElementType.TABLE_COLOMN,
                            children = cols.trimWhiteSpaces()
                        )
                        val child = build(colNode)
                        tableColumns.add(child)
                        cols = mutableListOf()
                    }

                    val tableNode = ComposerNode(
                        type = IElementType.TABLE,
                        children = tableColumns
                    )
                    childNodes.add(tableNode)
                }

                IElementType.COLOR_START -> {
                    index++
                    val start = index
                    while (children.getOrNull(index) != null && children[index].type != IElementType.COLOR_END && children[index] != ComposerNode.EOF) {
                        index++
                    }

                    var colorValue = "#222222"
                    var subChild = children.subList(start, index)

                    val last = subChild.last()
                    if (last.type == IElementType.COLOR_HEX) {
                        colorValue = last.literal
                        subChild = subChild.dropLast(1)
                    }

                    index++ // skip the closing symbol
                    val coloredNode = ComposerNode(
                        type = IElementType.COLORED,
                        literal = colorValue,
                        children = subChild.trimWhiteSpaces()
                    )
                    val child = build(coloredNode)
                    childNodes.add(child)
                }

                IElementType.ELEMENT -> {
                    index++
                    val start = index
                    while (children.getOrNull(index) != null && children[index].type != IElementType.NEWLINE && children[index] != ComposerNode.EOF) {
                        index++
                    }
                    val subChild = children.subList(start, index)
                    val nodeType = when (current.literal) {
                        "*" -> IElementType.ELEMENT_BULLET
                        "*o" -> IElementType.ELEMENT_UNCHECKED
                        "*x" -> IElementType.ELEMENT_CHECKED
                        else -> IElementType.ELEMENT
                    }

                    val elementNode = ComposerNode(
                        type = nodeType,
                        children = subChild.trimWhiteSpaces()
                    )
                    val child = build(elementNode)
                    childNodes.add(child)
                }

                else -> {
                    childNodes.add(children[index])
                    index++
                }
            }
        }

        return node.copy(children = childNodes)
    }

    private fun List<ComposerNode>.trimWhiteSpaces(): List<ComposerNode> {
        if (isEmpty()) {
            return emptyList()
        }

        if (size == 1) {
            val child = first()
            return if (child.type == IElementType.WHITESPACE || child.type == IElementType.NEWLINE) emptyList() else this
        } else {
            var modifiedList = this
            while (modifiedList.first().type == IElementType.WHITESPACE || modifiedList.first().type == IElementType.NEWLINE) {
                modifiedList = modifiedList.drop(1)
                if (modifiedList.isEmpty()) break
            }

            if (modifiedList.isEmpty()) {
                return emptyList()
            }

            while (modifiedList.last().type == IElementType.WHITESPACE || modifiedList.last().type == IElementType.NEWLINE) {
                modifiedList = modifiedList.dropLast(1)
                if (modifiedList.isEmpty()) break
            }

            return modifiedList.ifEmpty { emptyList() }
        }
    }
}