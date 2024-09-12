package com.naulian.modify.sheet

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import com.naulian.modify.PreviewScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    show: Boolean = true,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    dragHandle: (@Composable () -> Unit)? = null,
    bgColor: Color = Color(0xFFFFFFFF),
    shape: RoundedCornerShape = RoundedCornerShape(topStartPercent = 6, topEndPercent = 6),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            shape = shape,
            containerColor = bgColor,
            content = content,
            dragHandle = dragHandle
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun previewSheetState(): SheetState {
    return SheetState(
        skipPartiallyExpanded = false,
        density = LocalDensity.current,
        initialValue = SheetValue.Expanded,
        confirmValueChange = { true }
    )
}

//preview not working
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun BottomSheetPreview() {
    PreviewScreen(color = Color.Black) {

        var isVisible by remember {
            mutableStateOf(true)
        }

        BottomSheet(
            show = isVisible,
            sheetState = previewSheetState(),
            onDismissRequest = {
                isVisible = false
            }
        ) {
            BottomSheetHeader(title = "BottomSheet", onDismiss = { isVisible = false })
        }
    }
}