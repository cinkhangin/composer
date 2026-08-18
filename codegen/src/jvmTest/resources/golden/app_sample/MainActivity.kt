package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
data object Login : NavKey

@Serializable
data object Home : NavKey

@Serializable
data object StatCard : NavKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val backStack = rememberNavBackStack(Login)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Login> {
                LoginScreen(
                    onNavigateToHome = { backStack.add(Home) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Home> {
                HomeScreen()
            }
            entry<StatCard> {
                StatCardScreen()
            }
        },
    )
}
