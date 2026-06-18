package com.example.cybershield.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.cybershield.core.domain.util.Result

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val moduleRepository: ModuleRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    // Current user UID — never null here because NavigationRoot
    // only routes to HomeScreen when authState is non-null
    private val uid: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    // ── UI state ───────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        loadModules()
    }

    // ── Load user profile (real-time) ──────────────────────────────────
    private fun loadUserProfile() {
        viewModelScope.launch {
            userRepository
                .getUserProfile(uid)
                .collect { result ->
                    when (result) {
                        is Result.Loading  ->
                            _uiState.update { it.copy(isUserLoading = true) }
                        is Result.Success ->
                            _uiState.update {
                                it.copy(
                                    isUserLoading = false,
                                    user          = result.data,
                                    userError     = null,
                                )
                            }
                        is Result.Error   ->
                            _uiState.update {
                                it.copy(
                                    isUserLoading = false,
                                    userError     = result.exception.message,
                                )
                            }
                    }
                }
        }
    }

    // ── Load modules (real-time, offline-first from Room cache) ────────
    private fun loadModules() {
        viewModelScope.launch {
            moduleRepository
                .getModules()
                .collect { result ->
                    when (result) {
                        is Result.Loading  ->
                            _uiState.update { it.copy(isModulesLoading = true) }
                        is Result.Success ->
                            _uiState.update {
                                it.copy(
                                    isModulesLoading = false,
                                    modules          = result.data,
                                    modulesError     = null,
                                )
                            }
                        is Result.Error   ->
                            _uiState.update {
                                it.copy(
                                    isModulesLoading = false,
                                    modulesError     = result.exception.message,
                                )
                            }
                    }
                }
        }
    }

    // ── Manual refresh (pull-to-refresh) ──────────────────────────────
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            moduleRepository.refreshModules()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ── Greeting based on time of day ──────────────────────────────────
    fun greeting(): String {
        val hour = java.util.Calendar.getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11  -> "Good morning"
            in 12..17 -> "Good afternoon"
            else      -> "Good evening"
        }
    }
}