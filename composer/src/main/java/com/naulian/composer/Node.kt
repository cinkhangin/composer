package com.naulian.composer

data class Node(
    val type: String = IElementType.ROOT,
    val literal: String = "",
    val children: List<Node> = emptyList()
) {
    companion object {
        val EOF = Node(IElementType.EOF, "")

        fun create(type: String, literal: CharSequence) = Node(type, literal.toString())
    }

    fun getHyperLink(): Pair<String, String> {
        if (literal.contains("@")) {
            val index = literal.indexOf("@")
            val hyper = literal.take(index)
            val link = literal.replace("$hyper@", "")

            return hyper to link
        }
        return "" to literal
    }

    fun getTableData(): List<List<Node>> {
        return children.map { col ->
            col.children.filterNot { it.type == IElementType.PIPE }
        }
    }

    fun getLangCodePair(): Pair<String, String> {
        if (literal.contains("\n")) {
            val index = literal.indexOf("\n")
            val lang = literal.take(index)
            val code = literal.drop(index).trim()

            if (lang.contains('.')) {
                return lang.replace(".", "") to code
            }
        }

        return "txt" to literal
    }
}