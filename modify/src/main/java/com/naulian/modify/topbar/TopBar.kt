package com.naulian.modify.topbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.naulian.modify.Preview
import androidx.compose.material3.TopAppBar as M3TopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    modifier: Modifier = Modifier,
    title: String = "",
    navigationIcon: ImageVector? = Icons.AutoMirrored.Rounded.ArrowBack,
    onClickNavIcon: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    color: Color = MaterialTheme.colorScheme.background,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    M3TopAppBar(
        title = { Text(text = title) },
        modifier = modifier,
        navigationIcon = {
            navigationIcon?.let { icon ->
                IconButton(onClick = { onClickNavIcon() }) {
                    Icon(imageVector = icon, contentDescription = null)
                }
            }
        },
        actions = actions,
        windowInsets = windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = color),
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun TopAppBarPreview() {
    Preview {
        TopAppBar(title = "Top App Bar")
    }
}