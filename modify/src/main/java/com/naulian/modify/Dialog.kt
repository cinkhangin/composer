package com.naulian.modify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.naulian.modify.button.Button
import com.naulian.modify.button.OutlinedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) = BasicAlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    properties = properties
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        tonalElevation = tonalElevation,
        content = content
    )
}


@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    contentArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) = BasicDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    properties = properties,
    shape = shape,
    tonalElevation = tonalElevation,
    containerColor = containerColor
) {
    Column(
        modifier = Modifier.padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = contentArrangement
    ) {
        title?.invoke()
        content()
        val btnWeight = if (dismissButton == null) 1f else 0.5f
        Row {
            dismissButton?.let { button ->
                Box(modifier = Modifier.weight(0.5f, true)) {
                    button()
                }

                Spacer(Modifier.width(16.dp))
            }

            Box(modifier = Modifier.weight(btnWeight, true)) {
                confirmButton()
            }
        }
    }
}

@Composable
fun AlertDialog(
    modifier: Modifier = Modifier,
    title: String = "",
    message: String = "",
    extras: @Composable ColumnScope.() -> Unit = {},
    primaryAction: String = "",
    secondaryAction: String = "",
    showDialog: Boolean,
    onDismissRequest: () -> Unit = {},
    onConfirmed: () -> Unit = {},
) {
    if (showDialog) {
        Dialog(
            modifier = modifier,
            title = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            },
            content = {
                if (message.isNotEmpty()) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(0.dp))
                }

                extras()
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            onDismissRequest = { onDismissRequest() },
            dismissButton = if (secondaryAction.isEmpty()) null else {
                {
                    OutlinedButton(
                        onClick = { onDismissRequest() },
                        modifier = Modifier.fillMaxWidth(),
                        height = 40.dp,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(text = secondaryAction, style = MaterialTheme.typography.titleSmall)
                    }
                }
            },
            confirmButton = {
                if (primaryAction.isNotEmpty()) {
                    Button(
                        onClick = { onConfirmed() },
                        modifier = Modifier.fillMaxWidth(),
                        height = 40.dp,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(text = primaryAction, style = MaterialTheme.typography.titleSmall)
                    }
                }
            },
        )
    }
}

@Composable
fun DeleteDialog(
    modifier: Modifier = Modifier,
    showDialog: Boolean,
    title: String = "Confirm Delete",
    message: String = "Are you sure you want to delete?",
    onDismissRequest: () -> Unit = {},
    onConfirmed: () -> Unit = {},
) {
    AlertDialog(
        modifier = modifier,
        showDialog = showDialog,
        title = title,
        message = message,
        onDismissRequest = onDismissRequest,
        onConfirmed = onConfirmed,
        primaryAction = "Delete",
        secondaryAction = "Cancel"
    )
}


@Composable
fun OneMessageDialog(
    message: String,
    modifier: Modifier = Modifier,
    showDialog: Boolean,
    onAction: () -> Unit = {},
) {
    if (showDialog) {
        Dialog(
            modifier = modifier,
            content = {
                if (message.isNotEmpty()) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        text = message,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider()
            },
            contentPadding = PaddingValues(0.dp),
            contentArrangement = Arrangement.spacedBy(0.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            onDismissRequest = { onAction() },
            confirmButton = {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { onAction() },
                    text = "OK", textAlign = TextAlign.Center,
                    color = Color.Blue
                )
            },
        )
    }
}

@Preview
@Composable
fun OneMessageDialogPreview() {
    MaterialTheme {
        OneMessageDialog(message = "Copied", showDialog = true)
    }
}

@Preview
@Composable
fun DeleteDialogPreview() {
    MaterialTheme {
        DeleteDialog(showDialog = true)
    }
}