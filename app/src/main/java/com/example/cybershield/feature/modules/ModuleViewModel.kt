package com.example.cybershield.feature.modules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.usecase.module.GetModuleByIdUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.dataOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModuleViewModel
    @Inject
    constructor(
        private val getModuleByIdUseCase: GetModuleByIdUseCase,
        private val moduleRepository: ModuleRepository,
        private val userRepository: UserRepository,
        private val getCurrentSession: GetCurrentSessionUseCase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val moduleId: String =
            requireNotNull(savedStateHandle["moduleId"]) {
                "ModuleViewModel requires a moduleId in the SavedStateHandle (ModuleRoute)"
            }

        private val uid: String
            get() = getCurrentSession()?.uid ?: ""
        private val _uiState = MutableStateFlow(ModuleUiState())
        val uiState: StateFlow<ModuleUiState> = _uiState.asStateFlow()

        private var savePositionJob: Job? = null
        private val _savedPositionMs = MutableStateFlow(0L)
        val savedPositionMs: StateFlow<Long> = _savedPositionMs.asStateFlow()

        private val _playbackSpeed = MutableStateFlow(1.0f)
        val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

        private val _isSavedPositionLoaded = MutableStateFlow(false)
        val isSavedPositionLoaded: StateFlow<Boolean> = _isSavedPositionLoaded.asStateFlow()

        private var loadJob: Job? = null

        init {
            loadModule()
            loadSavedPosition()
        }

        fun loadModule() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    getModuleByIdUseCase(moduleId).collect { result ->
                        when (result) {
                            is Result.Loading -> {
                                // Only show the full-screen spinner (which
                                // unmounts VideoPlayerComposable, tearing down and
                                // later re-creating its ExoPlayer) on the very
                                // first load. ON_RESUME triggers a background
                                // refresh of an already-loaded module; that must
                                // not interrupt playback or reset watch position.
                                _uiState.update { it.copy(isLoading = it.module == null) }
                            }
                            is Result.Success -> {
                                val completedModules =
                                    userRepository
                                        .getUserProfileOnce(uid)
                                        .dataOrNull
                                        ?.completedModules
                                        ?: emptyList()
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        module = result.data,
                                        isAlreadyCompleted = moduleId in completedModules,
                                        isStale = false,
                                        error = null,
                                    )
                                }
                            }
                            is Result.Error -> {
                                _uiState.update {
                                    if (result.isStale) {
                                        it.copy(isLoading = false, isStale = true)
                                    } else {
                                        it.copy(isLoading = false, error = result.exception.message)
                                    }
                                }
                            }
                        }
                    }
                }
        }

        private fun loadSavedPosition() {
            if (uid.isBlank()) {
                _isSavedPositionLoaded.value = true
                return
            }
            viewModelScope.launch {
                val pos = moduleRepository.getPlaybackPosition(moduleId, uid)
                _savedPositionMs.value = pos
                _isSavedPositionLoaded.value = true
            }
        }

        fun savePosition(positionMs: Long) {
            if (uid.isBlank()) return
            // Keep the in-memory value fresh, not just Room: if the player is
            // ever torn down and recreated (e.g. a background refresh that
            // still has to show the spinner), VideoPlayerComposable seeks to
            // whatever savedPositionMs currently holds. Previously this was
            // only ever set once, in loadSavedPosition() at init, so a
            // recreated player would seek back to that stale (often zero)
            // value instead of where playback actually was.
            _savedPositionMs.value = positionMs
            savePositionJob?.cancel()
            savePositionJob =
                viewModelScope.launch {
                    moduleRepository.savePlaybackPosition(moduleId, uid, positionMs)
                }
        }

        fun onVideoCompleted(watchedMs: Long) {
            if (uid.isBlank()) return
            viewModelScope.launch {
                _uiState.value.module ?: return@launch
                if (!_uiState.value.isAlreadyCompleted) {
                    // Server-side: marks completedModules and awards xpReward
                    // atomically (and idempotently) via completeModuleFn. See
                    // UserRepository.completeModule kdoc — this used to be two
                    // separate client writes, one of which (addXp) let a
                    // malicious client award itself arbitrary XP. watchedMs lets
                    // the server sanity-check that playback actually reached the
                    // end before crediting XP — see completeModule in modules.ts.
                    userRepository.completeModule(uid, moduleId, watchedMs)
                    _uiState.update {
                        it.copy(
                            showCompletionDialog = true,
                            isAlreadyCompleted = true,
                        )
                    }
                }
            }
        }

        fun onCompletionDialogDismissed() = _uiState.update { it.copy(showCompletionDialog = false) }

        fun setPlaybackSpeed(speed: Float) {
            _playbackSpeed.value = speed
        }
    }
