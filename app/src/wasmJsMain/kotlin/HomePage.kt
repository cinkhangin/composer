package composer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.model.DesignJson
import composer.model.Node
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.ComponentGlyph
import composer.ui.SquareIconButton
import composer.ui.Theme
import composer.ui.Tk
import composer.ui.ToolButton

/**
 * Landing / dashboard. Minimal but warm: a hero with a soft accent glow and the
 * primary actions, template cards, and the saved designs as a thumbnail grid —
 * each thumbnail is the design's real artboard map (its screens, drawn to scale).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomePage(ws: Workspace) {
    var files by remember { mutableStateOf(FileStore.list()) }
    fun refresh() { files = FileStore.list() }
    var importError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Tk.appBg)) {
        TopBar()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Hero(
                onNew = { ws.newDesign() },
                onImport = {
                    importTextFile(".json,application/json") { text ->
                        runCatching { DesignJson.decode(text) }
                            .onSuccess { importError = null; ws.newDesignFrom("Imported design", it) }
                            .onFailure { importError = "That file isn't a valid Composer design JSON." }
                    }
                },
                importError = importError,
            )

            Column(Modifier.widthIn(max = 1040.dp).fillMaxWidth()) {
                SectionLabel("Templates")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (t in templates) {
                        TemplateCard(t, Modifier.weight(1f)) { ws.newDesignFrom(t.name, t.build()) }
                    }
                }

                Spacer(Modifier.height(40.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Your designs")
                    Spacer(Modifier.weight(1f))
                    if (files.isNotEmpty()) {
                        BasicText(
                            "${files.size} ${if (files.size == 1) "design" else "designs"}",
                            style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NewDesignCard { ws.newDesign() }
                    for (file in files) {
                        FileCard(
                            file = file,
                            onOpen = { ws.open(file) },
                            onDelete = { FileStore.delete(file.id); refresh() },
                        )
                    }
                }
                if (files.isEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    BasicText(
                        "No designs yet — start with a template or a blank canvas.",
                        style = TextStyle(color = Tk.textMuted, fontSize = 12.5.sp),
                    )
                }

                Spacer(Modifier.height(48.dp))
                BasicText(
                    "Local-first — your designs live in this browser. Export as Kotlin or JSON anytime.",
                    style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp),
                )
            }
        }
    }
}

/** Headline + primary actions over a soft accent glow anchored to the top. */
@Composable
private fun Hero(onNew: () -> Unit, onImport: () -> Unit, importError: String?) {
    val glow = Tk.accent.copy(alpha = if (Theme.isDark) 0.14f else 0.08f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glow, Color.Transparent),
                        center = Offset(size.width / 2f, -size.height * 0.35f),
                        radius = size.width * 0.55f,
                    ),
                )
            }
            .padding(top = 64.dp, bottom = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText(
            "Design visually. Ship Compose.",
            style = TextStyle(color = Tk.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        )
        BasicText(
            "Draw your screens on a canvas — Composer generates clean,\ndeterministic Compose Multiplatform code.",
            style = TextStyle(color = Tk.textSecondary, fontSize = 14.5.sp, textAlign = TextAlign.Center, lineHeight = 21.sp),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolButton("New design", primary = true, icon = AppIconKind.Plus, onClick = onNew)
            ToolButton("Import JSON", icon = AppIconKind.File, onClick = onImport)
        }
        if (importError != null) {
            BasicText(importError, style = TextStyle(color = Tk.danger, fontSize = 12.5.sp))
        }
    }
}

/** Small uppercase overline — quieter than a heading, keeps sections airy. */
@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text.uppercase(),
        style = TextStyle(color = Tk.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp),
    )
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Tk.accent))
        Spacer(Modifier.size(10.dp))
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

/** Small "v0.1.0" pill next to the brand — [APP_VERSION] is the source of truth. */
@Composable
private fun VersionBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Tk.panelAlt)
            .border(1.dp, Tk.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        BasicText("v$APP_VERSION", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
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
            BasicText("G", style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
        Column {
            BasicText("Guest", style = TextStyle(color = Tk.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium))
            BasicText("Local workspace", style = TextStyle(color = Tk.textMuted, fontSize = 10.5.sp))
        }
    }
}

private val CARD_WIDTH = 244.dp
private val THUMB_HEIGHT = 128.dp
private val CARD_HEIGHT = 184.dp

/** Dashed "blank canvas" card — doubles as the empty state. */
@Composable
private fun NewDesignCard(onNew: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val stroke = if (hovered) Tk.accent else Tk.borderStrong
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .clip(RoundedCornerShape(Tk.r))
            .background(if (hovered) Tk.panel else Color.Transparent)
            .drawBehind {
                val sw = 1.dp.toPx()
                drawRoundRect(
                    color = stroke,
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(Tk.r.toPx()),
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))),
                )
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onNew() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Tk.accentSoft), contentAlignment = Alignment.Center) {
            AppIcon(AppIconKind.Plus, Modifier.size(20.dp), tint = Tk.accent)
        }
        Spacer(Modifier.height(10.dp))
        BasicText("New design", style = TextStyle(color = Tk.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(2.dp))
        BasicText("Blank canvas", style = TextStyle(color = Tk.textMuted, fontSize = 11.5.sp))
    }
}

@Composable
private fun FileCard(file: FileMeta, onOpen: () -> Unit, onDelete: () -> Unit) {
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

/**
 * The design's artboard map: its screens drawn to scale as rounded rects — a real
 * (if abstract) thumbnail, decoded from the stored JSON. Falls back to a glyph
 * when the file can't be decoded.
 */
@Composable
private fun ArtboardThumb(file: FileMeta, modifier: Modifier = Modifier) {
    val screens = remember(file.id, file.updatedAt) {
        val root = runCatching { FileStore.loadDesign(file.id)?.let { DesignJson.decode(it) } }.getOrNull()
        when (root) {
            is Node.Artboard -> root.composables.filterIsInstance<Node.Composable>()
            is Node.Composable -> listOf(root) // pre-Artboard save
            else -> emptyList()
        }
    }
    if (screens.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            ComponentGlyph("Composable", Modifier.size(26.dp))
        }
        return
    }
    val fill = Tk.elevated
    val stroke = Tk.borderStrong
    Canvas(modifier) {
        val pad = 14.dp.toPx()
        val minX = screens.minOf { it.x }.toFloat()
        val minY = screens.minOf { it.y }.toFloat()
        val maxX = screens.maxOf { it.x + it.width }.toFloat()
        val maxY = screens.maxOf { it.y + it.height }.toFloat()
        val cw = (maxX - minX).coerceAtLeast(1f)
        val ch = (maxY - minY).coerceAtLeast(1f)
        val scale = minOf((size.width - 2 * pad) / cw, (size.height - 2 * pad) / ch)
        val ox = (size.width - cw * scale) / 2f
        val oy = (size.height - ch * scale) / 2f
        val r = CornerRadius(3.dp.toPx())
        for (s in screens) {
            val tl = Offset(ox + (s.x - minX) * scale, oy + (s.y - minY) * scale)
            val sz = Size(s.width * scale, s.height * scale)
            drawRoundRect(fill, topLeft = tl, size = sz, cornerRadius = r)
            drawRoundRect(stroke, topLeft = tl, size = sz, cornerRadius = r, style = Stroke(1.dp.toPx()))
        }
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
            .clickable(interactionSource = interaction, indication = null) { onOpen() },
    ) {
        // Preview band: a quiet accent wash with the template's glyph.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(Brush.verticalGradient(listOf(Tk.accentSoft, Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Tk.panel), contentAlignment = Alignment.Center) {
                ComponentGlyph(template.glyph, Modifier.size(20.dp))
            }
        }
        Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(template.name, style = TextStyle(color = Tk.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium))
            BasicText(
                template.description,
                style = TextStyle(color = Tk.textMuted, fontSize = 11.5.sp),
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
