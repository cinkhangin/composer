package com.example.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun StatCardScreen(viewModel: StatCardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    StatCardScreenUI()
}
