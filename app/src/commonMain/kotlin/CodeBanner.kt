package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.Tk

/** Slim in-panel banner (SaveErrorBanner is App-file-private; same visual recipe). */
@Composable
internal fun CodeBanner(message: String, danger: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (danger) Tk.dangerSoft else Tk.panelAlt)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        BasicText(
            message,
            style = TextStyle(color = if (danger) Tk.danger else Tk.textSecondary, fontSize = 12.sp),
        )
    }
}
