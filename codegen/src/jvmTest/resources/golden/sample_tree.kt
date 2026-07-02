import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Screen1() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Welcome to Composer", modifier = Modifier.padding(8.dp))

        Row(modifier = Modifier.padding(8.dp)) {
            Button(onClick = {}) {
                Text("Primary")
            }

            Spacer(modifier = Modifier.size(12.dp, 0.dp))

            Button(onClick = {}) {
                Text("Secondary")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color(0xFFE0E0E0)),
        ) {
            Text("A boxed label", modifier = Modifier.padding(16.dp))
        }
    }
}
