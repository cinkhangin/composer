package com.naulian.modify.sheet

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.naulian.modify.Preview
import com.naulian.modify.button.IconButton

@Composable
fun BottomSheetHeader(
    modifier: Modifier = Modifier,
    title: String,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    closeIcon: ImageVector = Icons.Rounded.Close,
    onDismiss: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = verticalAlignment
    ) {
        onDismiss?.let {
            IconButton(
                imageVector = closeIcon,
                contentDescription = "Close Icon",
                onClick = it
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
        )

        onDismiss?.let {
            IconButton(
                imageVector = closeIcon,
                contentDescription = "Close Icon",
                onClick = it,
                tint = Color.Transparent
            )
        }
    }
}

@Preview
@Composable
fun BottomSheetHeaderPreview() {
    Preview {
        BottomSheetHeader(title = "Bottom Sheet") {}
    }
}