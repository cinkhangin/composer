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

class ASTBuilder(private val nodes: List<CPSNode>) {

    fun buildTyped(node: CPSNode = CPSNode(children = nodes)): CPSNode {
        if (node.children.isEmpty()) {
            return node
        }

        var index = 0
        val children = node.children
        val childNodes = mutableListOf<CPSNode>()
        while (children.getOrNull(index) != null && children[index].type != IElementType.EOF) {
            val current = children[index]
            when (current.type) {
                in textTypes -> {
                    val start = index
                    while (children.getOrNull(index) != null && children[index].type in textTypes && children[index] != CPSNode.EOF) {
                        index++
                    }

                    val subChild = children.subList(start, index)
                    val paragraphNode = CPSNode(
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
                        && children[index] != CPSNode.EOF
                    ) {
                        index++
                    }
                    val subChild = children.subList(start, index)
                        .filter { it.type != IElementType.NEWLINE }
                        .map { buildTyped(it) }

                    val elementNode = CPSNode(
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

    fun build(node: CPSNode = CPSNode(children = nodes)): CPSNode {
        if (node.children.isEmpty()) {
            return node
        }

        var index = 0
        val children = node.children
        val childNodes = mutableListOf<CPSNode>()
        while (children.getOrNull(index) != null && children[index].type != IElementType.EOF) {
            val current = children[index]
            when (current.type) {
                IElementType.HEADER -> {
                    index++
                    val start = index
                    while (children.getOrNull(index) != null && children[index].type != IElementType.NEWLINE && children[index] != CPSNode.EOF) {
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
                    val h1Node = CPSNode(
                        type = nodeType,
                        children = subChild.trimWhiteSpaces()
                    )
                    val child = build(h1Node)
                    childNodes.add(child)
                }

                IElementType.BLOCK_SYMBOL -> {
                    index++
                    val start = index
                    while (children.getOrNull(index) != null && children[index].literal != current.literal && children[index] != CPSNode.EOF) {
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
                    val blockNode = CPSNode(
                        type = nodeType,
                        children = subChild.trimWhiteSpaces()
                    )
                    val child = build(blockNode)
                    childNodes.add(child)
                }

                IElementType.TABLE_START -> {
                    index++
                    val start = index
                    while (children.getOrNull(index) != null && children[index].type != IElementType.TABLE_END && children[index] != CPSNode.EOF) {
                        index++
                    }
                    val subChild = children.subList(start, index).trimWhiteSpaces()
                    index++ // skip the closing symbol

                    val tableColumns = mutableListOf<CPSNode>()
                    var cols = mutableListOf<CPSNode>()

                    subChild.forEach {
                        if (it.type == IElementType.NEWLINE) {
                            val colNode = CPSNode(
                                type = IElementType.TABLE_COLOMN,
                                children = cols.trimWhiteSpaces()
                            )
                            val child = build(colNode)
                            tableColumns.add(child)
                            cols = mutableListOf()
                        } else cols.add(it)
                    }

                    if (cols.isNotEmpty()) {
                        val colNode = CPSNode(
                            type = IElementType.TABLE_COLOMN,
                            children = cols.trimWhiteSpaces()
                        )
                        val child = build(colNode)
                        tableColumns.add(child)
                        cols = mutableListOf()
                    }

                    val tableNode = CPSNode(
                        type = IElementType.TABLE,
                        children = tableColumns
                    )
                    childNodes.add(tableNode)
                }

                IElementType.COLOR_START -> {
                    index++
                    val start = index
                    while (children.getOrNull(index) != null && children[index].type != IElementType.COLOR_END && children[index] != CPSNode.EOF) {
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
                    val coloredNode = CPSNode(
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
                    while (children.getOrNull(index) != null && children[index].type != IElementType.NEWLINE && children[index] != CPSNode.EOF) {
                        index++
                    }
                    val subChild = children.subList(start, index)
                    val nodeType = when (current.literal) {
                        "*" -> IElementType.ELEMENT_BULLET
                        "*o" -> IElementType.ELEMENT_UNCHECKED
                        "*x" -> IElementType.ELEMENT_CHECKED
                        else -> IElementType.ELEMENT
                    }

                    val elementNode = CPSNode(
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

    private fun List<CPSNode>.trimWhiteSpaces(): List<CPSNode> {
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