package com.example.cybershield.feature.home

import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.model.User

data class HomeUiState(
    // User profile
    val user: User? = null,
    val isUserLoading: Boolean = true,
    val userError: String? = null,
    // Modules list
    val modules: List<Module> = emptyList(),
    val isModulesLoading: Boolean = true,
    val modulesError: String? = null,
    // Pull-to-refresh
    val isRefreshing: Boolean = false,
) {
    // True when both profile and modules have finished their first load
    val isLoading: Boolean
        get() = isUserLoading || isModulesLoading

    // Modules the user has NOT yet completed — shown first
    val pendingModules: List<Module>
        get() =
            modules.filter {
                it.id !in (user?.completedModules ?: emptyList())
            }

    // Modules the user HAS completed
    val completedModules: List<Module>
        get() =
            modules.filter {
                it.id in (user?.completedModules ?: emptyList())
            }
}
