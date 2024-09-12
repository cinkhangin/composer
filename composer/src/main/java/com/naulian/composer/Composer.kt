package com.naulian.composer

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun CharSequence.str(): String = toString().trim()

const val dollarSign = "$"
const val mil = "${dollarSign}millis"
const val sec = "${dollarSign}second"
const val min = "${dollarSign}minute"
const val hour = "${dollarSign}hour"
const val today = "${dollarSign}day"
const val month = "${dollarSign}month"
const val year = "${dollarSign}year"
const val date = "${dollarSign}date"
const val dateTime = "${dollarSign}datetime"
const val time = "${dollarSign}time"

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