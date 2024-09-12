package com.naulian.modify.chip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naulian.modify.bold
import com.naulian.modify.themeColors
import com.naulian.modify.themeStyles

@Composable
fun Chip(
    modifier: Modifier = Modifier,
    label: String,
    height: Dp = 25.dp,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = 12.dp,
        vertical = 0.dp
    ),
    allCaps: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    checkContainerColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    checkTextColor: Color = MaterialTheme.colorScheme.onPrimary,
    textStyle: TextStyle = themeStyles.labelSmall.bold(),
    outline: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp),
    isChecked: Boolean = false,
    showCheckIcon: Boolean = true,
) {
    Box(
        modifier = if (outline) modifier
            .height(height)
            .border(
                width = 1.dp, shape = shape,
                color = containerColor
            )
        else modifier
            .height(height)
            .background(if (isChecked) checkContainerColor else containerColor, shape),
    ) {
        Row(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isChecked && showCheckIcon) {
                Icon(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(14.dp),
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = checkTextColor
                )
            }
            Text(
                text = if (allCaps) label.uppercase() else label,
                style = textStyle,
                color = if (isChecked) checkTextColor else textColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SmallChip(
    label: String,
    allCaps: Boolean = false,
    containerColor: Color = themeColors.primaryContainer,
    textColor: Color = themeColors.onPrimaryContainer,
    outline: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp)
) {

    Chip(
        height = 18.dp,
        label = label,
        allCaps = allCaps,
        containerColor = containerColor,
        textColor = textColor,
        outline = outline,
        shape = shape
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
fun ChipPreview() {
    MaterialTheme {
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            SmallChip(label = "Chip")

            Chip(
                height = 25.dp,
                label = "Lowercase",
                shape = RoundedCornerShape(50),
                containerColor = Color.LightGray,
                textColor = Color.DarkGray,
                textStyle = themeStyles.labelMedium,
                checkContainerColor = Color.Green,
                checkTextColor = Color.Green,
                isChecked = false
            )

            Chip(
                height = 25.dp,
                label = "Lowercase",
                shape = RoundedCornerShape(50),
                containerColor = Color.LightGray,
                textColor = Color.DarkGray,
                textStyle = themeStyles.labelMedium,
                checkContainerColor = Color.Green,
                checkTextColor = Color.Black,
                isChecked = true
            )

            Chip(
                height = 30.dp,
                label = "Uncheck Outline",
                shape = RoundedCornerShape(50),
                containerColor = themeColors.primary,
                textColor = themeColors.primary,
                textStyle = themeStyles.labelMedium,
                checkContainerColor = themeColors.primary,
                checkTextColor = themeColors.primary,
                isChecked = false,
                outline = true
            )

            Chip(
                height = 30.dp,
                label = "Checked",
                shape = RoundedCornerShape(50),
                containerColor = themeColors.primary,
                textColor = themeColors.primary,
                textStyle = themeStyles.labelMedium,
                checkContainerColor = themeColors.primary,
                checkTextColor = themeColors.onPrimary,
                isChecked = true,
                outline = false,
                showCheckIcon = false
            )
        }
    }
}