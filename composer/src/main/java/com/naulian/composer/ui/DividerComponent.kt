package com.naulian.composer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import com.naulian.composer.ComposerNode
import com.naulian.composer.IElementType


@Composable
fun DividerComponent(node: ComposerNode) {
    when (node.literal) {
        "" -> {}
        "br" -> Spacer(modifier = Modifier.height(1.dp))
        "line" -> HorizontalDivider()
        "dash" -> HorizontalDashDivider()
        else -> {
            if (node.literal.isDigitsOnly()) {
                val sizeInt = node.literal.toInt()
                Spacer(modifier = Modifier.height(sizeInt.dp))
            }
        }
    }
}

@Preview
@Composable
private fun DividerPreview() {
    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            DividerComponent(node = ComposerNode(IElementType.DIVIDER, "line"))
            DividerComponent(node = ComposerNode(IElementType.DIVIDER, "20"))
            DividerComponent(node = ComposerNode(IElementType.DIVIDER, "dash"))
        }
    }
}