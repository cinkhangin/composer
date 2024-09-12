package com.naulian.composer

class ComposerParser(private val source: String) {
    fun parse(): ComposerNode {
        val nodes = ComposerLexer(source).tokenize()
        val astBuilder = ASTBuilder(nodes)
        val treeNode = astBuilder.build()
        val typedNode = astBuilder.buildTyped(treeNode)
        return typedNode
    }
}

fun main() {
    val node = ComposerParser(COMPOSER_SAMPLE).parse()
    printNode(node)
}

private fun printNode(node: ComposerNode, level: Int = 0) {
    if (node.children.isEmpty()) {
        if (level > 0) {
            repeat(level) {
                print("|    ")
            }
        }
        println(node.type)
        return
    }

    if (level > 0) {
        repeat(level) {
            print("|    ")
        }
    }
    println(node.type)
    node.children.forEach {
        printNode(it, level + 1)
    }
}