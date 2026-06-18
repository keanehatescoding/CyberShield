package com.example.cybershield.feature.modules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.cybershield.ModuleRoute
import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.dataOrNull
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModuleViewModel @Inject constructor(
    private val moduleRepository: ModuleRepository,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val moduleId: String =
        savedStateHandle.toRoute<ModuleRoute>().moduleId

    private val uid: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(ModuleUiState())
    val uiState: StateFlow<ModuleUiState> = _uiState.asStateFlow()

    private val _savedPositionMs = MutableStateFlow(0L)
    val savedPositionMs: StateFlow<Long> = _savedPositionMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow() // was 'playbackspeed'

    init {
        loadModule()
        loadSavedPosition()
    }

    private fun loadModule() {
        viewModelScope.launch {
            moduleRepository
                .getModuleById(moduleId)
                .collect { result ->
                    when (result) {
                        is Result.Loading -> {
                            _uiState.update { it.copy(isLoading = true) }
                        }
                        is Result.Success -> {
                            val completedModules =
                                userRepository.getUserProfileOnce(uid)
                                    .dataOrNull
                                    ?.completedModules
                                    ?: emptyList()
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    module = result.data,
                                    isAlreadyCompleted = moduleId in completedModules,
                                )
                            }
                        }
                        is Result.Error -> {
                            _uiState.update {
                                it.copy(isLoading = false, error = result.exception.message)
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

    fun onCompletionDialogDismissed() =
        _uiState.update { it.copy(showCompletionDialog = false) }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }
}

data class ModuleUiState(
    val module: Module? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCompletionDialog: Boolean = false,
    val isAlreadyCompleted: Boolean = false,
)