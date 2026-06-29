package com.example.cybershield.feature.modules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.cybershield.ModuleRoute
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
            savedStateHandle.toRoute<ModuleRoute>().moduleId

        private val uid: String
            get() = getCurrentSession()?.uid ?: ""
        private val _uiState = MutableStateFlow(ModuleUiState())
        val uiState: StateFlow<ModuleUiState> = _uiState.asStateFlow()

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
                                _uiState.update { it.copy(isLoading = true) }
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
            viewModelScope.launch {
                val pos = moduleRepository.getPlaybackPosition(moduleId, uid)
                _savedPositionMs.value = pos
                _isSavedPositionLoaded.value = true
            }
        }

        fun savePosition(positionMs: Long) {
            viewModelScope.launch {
                moduleRepository.savePlaybackPosition(moduleId, uid, positionMs)
            }
        }

        fun onVideoCompleted() {
            viewModelScope.launch {
                val module = _uiState.value.module ?: return@launch
                if (!_uiState.value.isAlreadyCompleted) {
                    userRepository.markModuleCompleted(uid, moduleId)
                    userRepository.addXp(uid, module.xpReward)
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
