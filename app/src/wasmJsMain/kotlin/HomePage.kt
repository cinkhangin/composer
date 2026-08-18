package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.model.DesignJson
import composer.ui.Tk

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

internal val CARD_WIDTH = 244.dp
internal val THUMB_HEIGHT = 128.dp
internal val CARD_HEIGHT = 184.dp

/** The official GitHub mark (viewBox 98×96), tinted like any icon. */
internal val githubMark: ImageVector by lazy {
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
