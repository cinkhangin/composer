package com.naulian.modify

import androidx.compose.ui.graphics.Color
import android.graphics.Color as LegacyColor

fun Color.fromHex(hexColor: String): Color {
    val legacyColor = LegacyColor.parseColor(hexColor)
    return Color(legacyColor)
}