package composer

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import composer.ui.Tk

/** Small uppercase overline — quieter than a heading, keeps sections airy. */
@Composable
internal fun SectionLabel(text: String) {
    BasicText(
        text.uppercase(),
        style = TextStyle(color = Tk.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp),
    )
}
