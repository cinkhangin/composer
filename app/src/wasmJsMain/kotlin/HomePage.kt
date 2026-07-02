package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.BasicText
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.ComponentGlyph
import composer.ui.SquareIconButton
import composer.ui.Theme
import composer.ui.Tk
import composer.ui.ToolButton

/** Landing / dashboard: account header, "New design", and the list of saved files. */
@Composable
fun HomePage(ws: Workspace) {
    var files by remember { mutableStateOf(FileStore.list()) }
    fun refresh() { files = FileStore.list() }

    Column(modifier = Modifier.fillMaxSize().background(Tk.appBg)) {
        TopBar()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 1040.dp).fillMaxWidth().padding(vertical = 28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("Your designs", style = TextStyle(color = Tk.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.weight(1f))
                        ToolButton("New design", primary = true, icon = AppIconKind.Plus) { ws.newDesign() }
                    }
                    Spacer(Modifier.height(20.dp))

                    if (files.isEmpty()) {
                        EmptyState { ws.newDesign() }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (file in files) {
                                FileCard(
                                    file = file,
                                    onOpen = { ws.open(file) },
                                    onDelete = { FileStore.delete(file.id); refresh() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Tk.panel)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Tk.accent))
        Spacer(Modifier.size(10.dp))
        BasicText("Composer", style = TextStyle(color = Tk.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.weight(1f))
        ToolButton("", icon = if (Theme.isDark) AppIconKind.Sun else AppIconKind.Moon, onClick = Theme::toggle)
        Spacer(Modifier.size(12.dp))
        AccountChip()
    }
}

@Composable
private fun AccountChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.r))
            .background(Tk.panelAlt)
            .border(1.dp, Tk.border, RoundedCornerShape(Tk.r))
            .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Tk.accent), contentAlignment = Alignment.Center) {
            BasicText("G", style = TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
        Column {
            BasicText("Guest", style = TextStyle(color = Tk.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium))
            BasicText("Local workspace", style = TextStyle(color = Tk.textMuted, fontSize = 10.5.sp))
        }
    }
}

@Composable
private fun FileCard(file: FileMeta, onOpen: () -> Unit, onDelete: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tk.r))
            .background(if (hovered) Tk.elevated else Tk.panel)
            .border(1.dp, if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.r))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onOpen() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(Tk.rSm)).background(Tk.accentSoft), contentAlignment = Alignment.Center) {
            ComponentGlyph("Box", Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                file.name.ifBlank { "Untitled" },
                style = TextStyle(color = Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText("Compose design", style = TextStyle(color = Tk.textMuted, fontSize = 12.sp))
        }
        // Delete is permanent (no trash/undo), so require a confirm click first.
        var confirming by remember { mutableStateOf(false) }
        if (confirming) {
            BasicText(
                "Delete permanently?",
                style = TextStyle(color = Tk.danger, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            )
            ToolButton("Cancel", onClick = { confirming = false })
            SquareIconButton(AppIconKind.Trash, danger = true, onClick = { confirming = false; onDelete() })
        } else {
            ToolButton("Open", onClick = onOpen)
            SquareIconButton(AppIconKind.Trash, danger = true, onClick = { confirming = true })
        }
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tk.rLg))
            .background(Tk.panel)
            .border(1.dp, Tk.border, RoundedCornerShape(Tk.rLg))
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(Tk.r)).background(Tk.accentSoft), contentAlignment = Alignment.Center) {
            AppIcon(AppIconKind.Plus, Modifier.size(26.dp), tint = Tk.accent)
        }
        BasicText("No designs yet", style = TextStyle(color = Tk.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        BasicText("Create your first Compose design.", style = TextStyle(color = Tk.textMuted, fontSize = 13.sp))
        Spacer(Modifier.height(2.dp))
        ToolButton("New design", primary = true, icon = AppIconKind.Plus, onClick = onNew)
    }
}
