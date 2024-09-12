package com.naulian.composer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.naulian.composer.IElementType
import com.naulian.composer.ComposerNode

@Composable
fun ImageComponent(modifier: Modifier = Modifier, node: ComposerNode) {
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isError = false
    }

    if (!isError) {
        AsyncImage(
            model = node.literal,
            modifier = modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small),
            contentDescription = "Atx Picture",
            contentScale = ContentScale.FillWidth,
            onError = { isError = true },
        )
    }
}

@Preview
@Composable
private fun ImageBlockPreview() {
    MaterialTheme {
        val node = ComposerNode(
            type = IElementType.IMAGE,
            literal = "https://picsum.photos/id/67/300/200",
        )
        ImageComponent(
            modifier = Modifier.padding(16.dp),
            node = node
        )
    }
}