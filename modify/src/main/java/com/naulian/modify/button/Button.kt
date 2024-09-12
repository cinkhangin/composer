package com.naulian.modify.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naulian.modify.PreviewColumn
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.OutlinedButton as M3OutlineButton

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    M3Button(
        onClick = onClick,
        modifier = modifier.height(height),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    borderThickness: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {

    val borderStroke: BorderStroke
    val buttonColors: ButtonColors

    if (enabled) {
        borderStroke = BorderStroke(borderThickness, color)
        buttonColors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    } else {
        borderStroke = ButtonDefaults.outlinedButtonBorder
        buttonColors = ButtonDefaults.outlinedButtonColors()
    }

    M3OutlineButton(
        onClick = onClick,
        modifier = modifier.height(height),
        enabled = enabled,
        shape = shape,
        border = borderStroke,
        contentPadding = contentPadding,
        colors = buttonColors,
        content = content
    )

}

@Preview
@Composable
private fun ButtonPreview() {
    PreviewColumn {
        Button(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            onClick = {}
        ) {
            Text(text = "Button")
        }

        OutlinedButton(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            enabled = false,
            onClick = {},
            color = Color.Red
        ) {
            Text(text = "Button")
        }
    }
}