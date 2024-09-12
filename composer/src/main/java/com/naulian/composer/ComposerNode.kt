package com.naulian.composer

data class ComposerNode(
    val type: String = IElementType.ROOT,
    val literal: String = "",
    val children: List<ComposerNode> = emptyList()
) {
    companion object {
        val EOF = ComposerNode(IElementType.EOF, "")
        fun create(type: String, literal: CharSequence) = ComposerNode(type, literal.toString())
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

    fun getTableData(): List<List<ComposerNode>> {
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