package com.example.cybershield.feature.modules

import com.example.cybershield.core.domain.model.Module

data class ModuleUiState(
    val module: Module? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCompletionDialog: Boolean = false,
    val isAlreadyCompleted: Boolean = false,
    val isStale: Boolean = false
)