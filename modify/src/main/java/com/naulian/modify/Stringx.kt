package com.naulian.modify

fun String.paragraph(): String {
    return trimIndent().replace("\n", " ")
}