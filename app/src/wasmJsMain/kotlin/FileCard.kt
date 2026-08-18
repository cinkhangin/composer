package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.AppIconKind
import composer.ui.SquareIconButton
import composer.ui.Tk
import composer.ui.ToolButton

@Composable
internal fun FileCard(file: FileMeta, onOpen: () -> Unit, onDelete: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .clip(RoundedCornerShape(Tk.r))
            .background(if (hovered) Tk.elevated else Tk.panel)
            .border(1.dp, if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.r))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onOpen() },
    ) {
        Box(Modifier.fillMaxWidth().height(THUMB_HEIGHT).background(Tk.panelAlt)) {
            ArtboardThumb(file, Modifier.fillMaxSize())
            if (hovered && !confirming) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    SquareIconButton(AppIconKind.Trash, danger = true, tip = "Delete", onClick = { confirming = true })
                }
            }
        }
        // Footer: name + edited date, or the delete confirmation (permanent, no trash).
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (confirming) {
                BasicText(
                    "Delete permanently?",
                    style = TextStyle(color = Tk.danger, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                ToolButton("Keep", onClick = { confirming = false })
                Spacer(Modifier.size(6.dp))
                SquareIconButton(AppIconKind.Trash, danger = true, onClick = { confirming = false; onDelete() })
            } else {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        file.name.ifBlank { "Untitled" },
                        style = TextStyle(color = Tk.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(editedLabel(file.updatedAt), style = TextStyle(color = Tk.textMuted, fontSize = 11.5.sp))
                }
            }
        }
    }
}
