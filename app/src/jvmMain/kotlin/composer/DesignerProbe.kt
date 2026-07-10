package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import javax.swing.JComponent

/**
 * J0 runtime spike: proves the bundled Compose Desktop runtime (CMP 1.11.1 +
 * Material3, platform-patched coroutines) renders and handles input inside the
 * IDE process — the GO/NO-GO gate for the in-process designer. TEMPORARY:
 * replaced by the real designer panel entry point.
 */
fun createProbePanel(): JComponent = ComposePanel().apply {
    setContent {
        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var clicks by remember { mutableStateOf(0) }
                Text("Compose-in-IDE probe", style = MaterialTheme.typography.titleMedium)
                Text("skiko + Material3 render OK")
                Button(onClick = { clicks++ }) {
                    Text("Clicked $clicks times")
                }
                val dispatcherStatus = remember {
                    try {
                        "Main dispatcher: " + kotlinx.coroutines.Dispatchers.Main.toString()
                    } catch (t: Throwable) {
                        "Main dispatcher FAILED: " + t.message
                    }
                }
                Text(dispatcherStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
