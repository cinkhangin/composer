package com.example.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StatCardUiState(
    val isLoading: Boolean = false,
)

class StatCardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StatCardUiState())
    val uiState: StateFlow<StatCardUiState> = _uiState.asStateFlow()
}
