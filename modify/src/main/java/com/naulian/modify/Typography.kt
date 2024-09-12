package com.naulian.modify

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

fun TextStyle.normal() = copy(fontWeight = FontWeight.Normal)
fun TextStyle.bold() = copy(fontWeight = FontWeight.Bold)