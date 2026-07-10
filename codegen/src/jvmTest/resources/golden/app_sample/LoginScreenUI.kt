package com.example.app

import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun LoginScreenUI(onNavigateToHome: () -> Unit = {}, onBack: () -> Unit = {}) {
    Text("Welcome")

    Button(onClick = onNavigateToHome) {
        Text("Sign in")
    }

    FloatingActionButton(onClick = onBack) {
    }
}
