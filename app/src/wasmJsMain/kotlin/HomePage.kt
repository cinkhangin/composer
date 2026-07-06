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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.material3.Icon
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
                    // Built-in templates — a separate section, never mixed with the user's files.
                    BasicText("Start from a template", style = TextStyle(color = Tk.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (t in templates) {
                            TemplateCard(t, Modifier.weight(1f)) { ws.newDesignFrom(t.name, t.build()) }
                        }
                    }
                    Spacer(Modifier.height(32.dp))

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
        GitHubButton()
        Spacer(Modifier.size(8.dp))
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
            BasicText(editedLabel(file.updatedAt), style = TextStyle(color = Tk.textMuted, fontSize = 12.sp))
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
            SquareIconButton(AppIconKind.Trash, danger = true, tip = "Delete", onClick = { confirming = true })
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

@Composable
private fun TemplateCard(template: Template, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Tk.r))
            .background(if (hovered) Tk.elevated else Tk.panel)
            .border(1.dp, if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.r))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onOpen() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(Tk.rSm)).background(Tk.accentSoft), contentAlignment = Alignment.Center) {
            ComponentGlyph(template.glyph, Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(template.name, style = TextStyle(color = Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            BasicText(
                template.description,
                style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The official GitHub mark (viewBox 98×96), tinted like any icon. */
private val githubMark: ImageVector by lazy {
    ImageVector.Builder(name = "github", defaultWidth = 18.dp, defaultHeight = 18.dp, viewportWidth = 98f, viewportHeight = 96f)
        .addPath(
            pathData = addPathNodes(
                "M48.854 0C21.839 0 0 22 0 49.217c0 21.756 13.993 40.172 33.405 46.69 2.427.49 3.316-1.059 " +
                    "3.316-2.362 0-1.141-.08-5.052-.08-9.127-13.59 2.934-16.42-5.867-16.42-5.867-2.184-5.704-5.42-7.17-5.42-7.17-4.448-3.015.324-3.015.324-3.015 " +
                    "4.934.326 7.523 5.052 7.523 5.052 4.367 7.496 11.404 5.378 14.235 4.074.404-3.178 1.699-5.378 3.074-6.6-10.839-1.141-22.243-5.378-22.243-24.283 " +
                    "0-5.378 1.94-9.778 5.014-13.2-.485-1.222-2.184-6.275.486-13.038 0 0 4.125-1.304 13.426 5.052a46.97 46.97 0 0 1 12.214-1.63c4.125 0 8.33.571 " +
                    "12.213 1.63 9.302-6.356 13.427-5.052 13.427-5.052 2.67 6.763.97 11.816.485 13.038 3.155 3.422 5.015 7.822 5.015 13.2 0 18.905-11.404 " +
                    "23.06-22.324 24.283 1.78 1.548 3.316 4.481 3.316 9.126 0 6.6-.08 11.897-.08 13.526 0 1.304.89 2.853 3.316 2.364 19.412-6.52 " +
                    "33.405-24.935 33.405-46.691C97.707 22 75.788 0 48.854 0z",
            ),
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        )
        .build()
}

@Composable
private fun GitHubButton() {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(Tk.rSm))
            .background(if (hovered) Tk.elevated else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { openUrl("https://github.com/cinkhangin/composer") },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = githubMark,
            contentDescription = "GitHub repository",
            modifier = Modifier.size(18.dp),
            tint = if (hovered) Tk.textPrimary else Tk.textSecondary,
        )
    }
}
