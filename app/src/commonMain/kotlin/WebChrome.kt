package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.codegen.CodeGen
import composer.model.DesignJson
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.BrandLogo
import composer.ui.HDivider
import composer.ui.SquareIconButton
import composer.ui.Theme
import composer.ui.Tk
import composer.ui.TkMenu
import composer.ui.TkMenuItem
import composer.ui.ToolButton

/** Website-only document toolbar. Android Studio keeps its smaller host-owned toolbar. */
@Composable
internal fun WebToolbar(state: EditorState, ws: Workspace) {
    Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WebFileMenu(state, ws)
            WebProjectTitle(ws)
            WebSaveStatus(ws)
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenSizeControl(state)
            Box(Modifier.width(1.dp).height(20.dp).background(Tk.border))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                WebViewSegment("Design", AppIconKind.Design, !state.showCode) { state.setCodeView(false) }
                WebViewSegment("Code", AppIconKind.Code, state.showCode) { state.setCodeView(true) }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SquareIconButton(AppIconKind.Undo, enabled = state.canUndo, tip = "Undo") { state.undo() }
            SquareIconButton(AppIconKind.Redo, enabled = state.canRedo, tip = "Redo") { state.redo() }
            SquareIconButton(
                if (Theme.isDark) AppIconKind.Sun else AppIconKind.Moon,
                tip = if (Theme.isDark) "Light mode" else "Dark mode",
                onClick = Theme::toggle,
            )
            WebExportMenu(state)
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Tk.accentSoft)
                    .border(1.dp, Tk.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("G", style = TextStyle(Tk.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun WebFileMenu(state: EditorState, ws: Workspace) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(Tk.rSm)).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            BrandLogo(Modifier.size(28.dp))
        }
        TkMenu(expanded = open, onDismissRequest = { open = false }) {
            TkMenuItem("New design") { open = false; ws.newDesign() }
            TkMenuItem("Save") { open = false; ws.saveOverwriting(state.root) }
            TkMenuItem("Import JSON…") {
                open = false
                importTextFile(".json,application/json") { text ->
                    runCatching { DesignJson.decode(text) }
                        .onSuccess { ws.importError = null; state.load(it) }
                        .onFailure { ws.importError = "That file isn't a valid Composer design JSON." }
                }
            }
            HDivider()
            TkMenuItem("Back to home") { open = false; ws.home() }
            BasicText(
                "Composer v$APP_VERSION",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = TextStyle(Tk.textMuted, fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun WebProjectTitle(ws: Workspace) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = ws.currentName,
        onValueChange = { ws.currentName = it },
        singleLine = true,
        interactionSource = interaction,
        cursorBrush = SolidColor(Tk.accent),
        textStyle = TextStyle(Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .widthIn(min = 80.dp, max = 220.dp)
            .clip(RoundedCornerShape(Tk.rSm))
            .background(if (hovered || focused) Tk.panelAlt else Color.Transparent)
            .border(
                1.dp,
                if (focused) Tk.accent else if (hovered) Tk.borderStrong else Color.Transparent,
                RoundedCornerShape(Tk.rSm),
            )
            .hoverable(interaction)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun WebSaveStatus(ws: Workspace) {
    val (label, color) = when (ws.saveStatus) {
        SaveStatus.Saved -> "Saved" to Tk.textMuted
        SaveStatus.Saving -> "Saving…" to Tk.textSecondary
        SaveStatus.Error -> "Save failed" to Tk.danger
    }
    BasicText(label, style = TextStyle(color, fontSize = 11.5.sp))
}

@Composable
private fun WebViewSegment(label: String, icon: AppIconKind, active: Boolean, onClick: () -> Unit) {
    val foreground = if (active) Color.White else Tk.textSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.rSm))
            .background(if (active) Tk.accent else Tk.panelAlt)
            .border(1.dp, if (active) Tk.accent else Tk.border, RoundedCornerShape(Tk.rSm))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(icon, Modifier.size(13.dp), foreground)
        BasicText(label, style = TextStyle(foreground, fontSize = 12.5.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun WebExportMenu(state: EditorState) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton("Export", primary = true, icon = AppIconKind.Share) { open = true }
        TkMenu(expanded = open, onDismissRequest = { open = false }) {
            TkMenuItem("Export .kt") {
                open = false
                downloadText("Screens.kt", CodeGen.generate(state.root), "text/plain")
            }
            TkMenuItem("Export JSON") {
                open = false
                downloadText("composer-design.json", DesignJson.encode(state.root), "application/json")
            }
        }
    }
}

@Composable
internal fun WebErrorBanner(
    message: String,
    actionLabel: String = "Dismiss",
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Tk.rSm))
            .background(Tk.dangerSoft).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(message, modifier = Modifier.weight(1f), style = TextStyle(Tk.danger, fontSize = 13.sp))
        ToolButton(actionLabel, onClick = onAction)
    }
}
