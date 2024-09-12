package com.naulian.modify.field

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naulian.modify.Preview

@Composable
fun TextFieldDisplay(
    modifier: Modifier = Modifier,
    text: String = "",
    trailingIcon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
    ) {
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, modifier = Modifier.weight(1f))
            trailingIcon?.invoke()
        }
    }
}

@Preview
@Composable
fun TextFieldDisplayPreview() {
    Preview {
        TextFieldDisplay(
            modifier = Modifier.padding(16.dp),
            text = "John Smith",
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.LocationOn, contentDescription = null
                )
            }
        )
    }
}