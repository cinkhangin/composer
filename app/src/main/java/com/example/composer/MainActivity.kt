package com.example.composer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.naulian.composer.COMPOSER_SAMPLE
import com.naulian.composer.ui.ComposerUI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MainContent()
                }
            }
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    LazyColumn {
        item {
            ComposerUI(
                modifier = Modifier.padding(16.dp),
                source = COMPOSER_SAMPLE,
                onClickLink = {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MaterialTheme {
        MainContent()
    }
}