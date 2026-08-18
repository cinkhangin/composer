package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import composer.ui.Tk

@Composable
internal fun DropLine(modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).padding(horizontal = 4.dp).background(Tk.accent))
}
