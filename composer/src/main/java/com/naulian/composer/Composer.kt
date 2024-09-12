package com.naulian.composer

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun CharSequence.str(): String = toString().trim()

const val dollarSign = "$"
const val mil = "mdx.millis"
const val sec = "mdx.second"
const val min = "mdx.minute"
const val hour = "mdx.hour"
const val today = "mdx.day"
const val month = "mdx.month"
const val year = "mdx.year"
const val date = "mdx.date"
const val dateTime = "mdx.datetime"
const val time = "mdx.time"

val currentMillis = System.currentTimeMillis()

val adhocMap = hashMapOf(
    mil to currentMillis.toString(),
    sec to formattedDateTime("ss"),
    min to formattedDateTime("mm"),
    hour to formattedDateTime("hh"),
    today to formattedDateTime("dd"),
    month to formattedDateTime("MM"),
    year to formattedDateTime("yyyy"),
    date to formattedDateTime("dd/MM/yyyy"),
    dateTime to formattedDateTime("dd/MM/yyyy hh:mm:ss a"),
    time to formattedDateTime("hh:mm:ss a")
)

fun formattedDateTime(pattern: String): String {
    val localDateTime = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern(pattern)
    return localDateTime.format(formatter)
}