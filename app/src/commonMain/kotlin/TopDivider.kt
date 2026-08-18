package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import composer.ui.Tk

@Composable
internal fun TopDivider() {
    Box(Modifier.padding(horizontal = 2.dp)) {
        Box(Modifier.width(1.dp).height(20.dp).background(Tk.border))
    }
}
