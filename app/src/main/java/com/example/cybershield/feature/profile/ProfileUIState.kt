package com.example.cybershield.feature.profile

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.model.User

data class ProfileUiState(
    val user:         User?           = null,
    val certificates: List<Certificate> = emptyList(),
    val isLoading:    Boolean          = true,
    val error:        String?          = null,
)