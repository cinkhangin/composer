@file:Suppress("unused")

package com.naulian.modify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val themeColors @Composable get() = MaterialTheme.colorScheme
val themeStyles @Composable get() = MaterialTheme.typography
val themeShapes @Composable get() = MaterialTheme.shapes


//methods to help when setting previews more efficiently
@Composable
internal fun Preview(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    MaterialTheme {
        Surface(modifier, content = content)
    }
}

@Composable
internal fun PreviewScreen(
    modifier: Modifier = Modifier,
    color: Color = themeColors.surface,
    content: @Composable () -> Unit = {}
) {
    MaterialTheme {
        Surface(modifier.fillMaxSize(), color = color, content = content)
    }
}


@Composable
internal fun PreviewBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    MaterialTheme {
        Surface {
            Box(modifier = modifier, content = content)
        }
    }
}

@Composable
internal fun PreviewColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    MaterialTheme {
        Surface {
            Column(
                modifier = modifier,
                content = content,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment
            )
        }
    }
}

@Composable
internal fun PreviewRow(
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit = {}
) {
    MaterialTheme {
        Surface {
            Row(
                modifier = modifier,
                content = content,
                verticalAlignment = verticalAlignment,
                horizontalArrangement = horizontalArrangement
            )
        }
    }
}