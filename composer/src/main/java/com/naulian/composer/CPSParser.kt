package com.naulian.composer

class Parser(private val source: String) {

    fun parse(): CPSNode {
        val nodes = CPSLexer(source).tokenize()
        //nodes.forEach(::println)
        val astBuilder = ASTBuilder(nodes)
        val treeNode = astBuilder.build()
        val typedNode = astBuilder.buildTyped(treeNode)
        return typedNode
    }
}

fun main() {
    val node = Parser(CPS_SAMPLE).parse()
    printNode(node)
}

private fun printNode(node: CPSNode, level: Int = 0) {
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