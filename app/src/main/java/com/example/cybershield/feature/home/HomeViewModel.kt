package com.example.cybershield.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.usecase.EnsureUserProfileUseCase
import com.example.cybershield.core.domain.usecase.ProfileRepairOutcome
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.cybershield.core.domain.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val observeAuthState: ObserveAuthStateUseCase,
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
        observeAuthSession()
        loadModules()
    }

    // ── Paged module lists — Room-backed, used by the LazyColumn instead
    // of materializing the full module list in memory. Re-derives from
    // uiState so the pager restarts (via flatMapLatest) whenever the
    // user's completed-module set actually changes, rather than on every
    // unrelated uiState update.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val completedModuleIds: StateFlow<List<String>> =
        uiState
            .map { it.user?.completedModules ?: emptyList() }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingModulesPaged: Flow<PagingData<Module>> =
        completedModuleIds
            .flatMapLatest { ids -> moduleRepository.getPendingModulesPaged(ids) }
            .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val completedModulesPaged: Flow<PagingData<Module>> =
        completedModuleIds
            .flatMapLatest { ids -> moduleRepository.getCompletedModulesPaged(ids) }
            .cachedIn(viewModelScope)

    // ── Load user profile (real-time) ──────────────────────────────────

    // Observe the auth session so the profile is (re)loaded as soon as the
    // user is signed in — even if getCurrentSession() was null at init time
    // because the session hadn't been restored yet.
    private fun observeAuthSession() {
        viewModelScope.launch {
            observeAuthState.observe().collect { session ->
                val uid = session?.uid.orEmpty()
                if (uid.isNotBlank()) {
                    loadUserProfile(uid)
                } else {
                    profileJob?.cancel()
                    _uiState.update {
                        it.copy(user = null, isUserLoading = false, userError = null)
                    }
                }
            }
        }
    }

    private fun loadUserProfile(uid: String = this.uid) {
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
                                    it.copy(
                                        isUserLoading = false,
                                        user = result.data,
                                        userError = null
                                    )
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
                                            it.copy(
                                                isUserLoading = false,
                                                userError = outcome.message
                                            )
                                        }

                                    is ProfileRepairOutcome.AlreadyAttempted ->
                                        _uiState.update {
                                            it.copy(
                                                isUserLoading = false,
                                                userError = outcome.message
                                            )
                                        }

                                    ProfileRepairOutcome.RepairSucceeded -> {
                                        // The repair created the profile doc; the live
                                        // getUserProfile listener (attached by
                                        // loadUserProfile) will re-emit Success shortly and
                                        // populate the user. Stop the spinner now so we don't
                                        // appear stuck waiting for that emission.
                                        _uiState.update { it.copy(isUserLoading = false) }
                                    }

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
