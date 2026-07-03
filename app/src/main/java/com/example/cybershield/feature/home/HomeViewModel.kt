package com.example.cybershield.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.usecase.EnsureUserProfileUseCase
import com.example.cybershield.core.domain.usecase.ProfileRepairOutcome
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val moduleRepository: ModuleRepository,
        private val getCurrentSession: GetCurrentSessionUseCase,
        private val ensureUserProfile: EnsureUserProfileUseCase,
        private val clock: Clock,
    ) : ViewModel() {
        private var profileJob: Job? = null
        private val uid: String
            get() = getCurrentSession()?.uid ?: ""

        // ── UI state ───────────────────────────────────────────────────────
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
        private var modulesJob: Job? = null

        init {
            loadUserProfile()
            loadModules()
        }

        // ── Load user profile (real-time) ──────────────────────────────────

        private fun loadUserProfile() {
            if (uid.isBlank()) return
            profileJob?.cancel()
            profileJob =
                viewModelScope.launch {
                    userRepository
                        .getUserProfile(uid)
                        .collect { result ->
                            when (result) {
                                is Result.Loading ->
                                    _uiState.update { it.copy(isUserLoading = true) }

                                is Result.Success -> {
                                    ensureUserProfile.onProfileLoadedSuccessfully()
                                    _uiState.update {
                                        it.copy(isUserLoading = false, user = result.data, userError = null)
                                    }
                                }

                                is Result.Error -> {
                                    val outcome =
                                        ensureUserProfile(
                                            error = result.exception,
                                            session = getCurrentSession(),
                                        )

                                    when (outcome) {
                                        is ProfileRepairOutcome.NotApplicable ->
                                            _uiState.update {
                                                it.copy(isUserLoading = false, userError = outcome.message)
                                            }

                                        is ProfileRepairOutcome.AlreadyAttempted ->
                                            _uiState.update {
                                                it.copy(isUserLoading = false, userError = outcome.message)
                                            }

                                        ProfileRepairOutcome.RepairSucceeded ->
                                            Unit // live profile flow will re-emit Success shortly

                                        ProfileRepairOutcome.RepairFailed ->
                                            _uiState.update {
                                                it.copy(
                                                    isUserLoading = false,
                                                    userError = "Couldn't set up your profile. Please check your connection and restart the app.",
                                                )
                                            }
                                    }
                                }
                            }
                        }
                }
        }

        // ── Load modules (one-shot Flow — network first, Room fallback) ────
        private fun loadModules() {
            modulesJob?.cancel() // ★ NEW — guard against overlapping collectors
            modulesJob =
                viewModelScope.launch {
                    moduleRepository
                        .getModules()
                        .collect { result ->
                            when (result) {
                                is Result.Loading ->
                                    _uiState.update { it.copy(isModulesLoading = true) }
                                is Result.Success ->
                                    _uiState.update {
                                        it.copy(
                                            isModulesLoading = false,
                                            modules = result.data,
                                            modulesError = null,
                                        )
                                    }
                                is Result.Error ->
                                    _uiState.update {
                                        it.copy(
                                            isModulesLoading = false,
                                            modulesError = result.exception.message,
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

                when (moduleRepository.refreshModules()) {
                    is Result.Success -> {
                        // Room now has fresh data — re-run loadModules() to pick it up
                        // since getModules() is a one-shot flow, not a live Room query
                        loadModules()
                        // loadModules() launches modulesJob asynchronously — join it so
                        // isRefreshing stays true until the fresh data has actually
                        // been collected into _uiState, not just until the job starts.
                        modulesJob?.join()
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(modulesError = "Couldn't refresh modules. Check your connection.")
                        }
                    }
                    Result.Loading -> Unit
                }

                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        // ★ NEW — clears a surfaced module error after the UI has shown it
        fun clearModulesError() {
            _uiState.update { it.copy(modulesError = null) }
        }

        // ── Greeting based on time of day ──────────────────────────────────
        fun greeting(): String {
            val hour = LocalTime.now(clock).hour
            return when (hour) {
                in 5..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
        }
    }
