package composer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.AppIconKind
import composer.ui.BrandLogo
import composer.ui.Theme
import composer.ui.Tk
import composer.ui.ToolButton

@Composable
internal fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLogo(Modifier.size(26.dp))
        Spacer(Modifier.size(9.dp))
        BasicText("Composer", style = TextStyle(color = Tk.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.size(8.dp))
        VersionBadge()
        Spacer(Modifier.weight(1f))
        GitHubButton()
        Spacer(Modifier.size(8.dp))
        ToolButton("", icon = if (Theme.isDark) AppIconKind.Sun else AppIconKind.Moon, onClick = Theme::toggle)
        Spacer(Modifier.size(12.dp))
        AccountChip()
    }
}
