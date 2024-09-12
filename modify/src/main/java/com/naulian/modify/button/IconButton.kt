package com.naulian.modify.button


import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naulian.modify.PreviewRow
import androidx.compose.material3.IconButton as M3IconButton

@Composable
fun IconButton(
    modifier: Modifier = Modifier,
    painter: Painter,
    iconSize: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
    contentDescription: String = ""
) {
    M3IconButton(modifier = modifier, onClick = onClick) {
        Icon(
            modifier = Modifier.size(iconSize),
            painter = painter,
            tint = tint,
            contentDescription = contentDescription
        )
    }
}

@Composable
fun IconButton(
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    iconSize: Dp = 24.dp,
    tint: Color = Color.Black,
    onClick: () -> Unit,
    contentDescription: String = ""
) {
    M3IconButton(modifier = modifier, onClick = onClick) {
        Icon(
            modifier = Modifier.size(iconSize),
            imageVector = imageVector,
            tint = tint,
            contentDescription = contentDescription
        )
    }
}

@Preview
@Composable
private fun IconButtonPreview() {
    PreviewRow {
        IconButton(
            imageVector = Icons.Rounded.Add,
            onClick = { /*TODO*/ }
        )
        IconButton(
            painter = rememberVectorPainter(image = Icons.Rounded.Add),
            onClick = { /*TODO*/ }
        )
    }
}