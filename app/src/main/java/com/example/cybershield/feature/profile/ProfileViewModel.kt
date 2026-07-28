package com.example.cybershield.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.repository.CertificateRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.usecase.auth.GetCurrentSessionUseCase
import com.example.cybershield.core.domain.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val certificateRepository: CertificateRepository,
        private val getCurrentSession: GetCurrentSessionUseCase,
    ) : ViewModel() {
        private val uid: String
            get() = getCurrentSession()?.uid ?: ""

        private val _uiState = MutableStateFlow(ProfileUiState())
        val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
        private var profileJob: Job? = null

        init {
            loadProfile()
            loadCertificates()
        }

        private fun loadProfile() {
            profileJob?.cancel()
            profileJob =
                viewModelScope.launch {
                    userRepository.getUserProfile(uid).collect { result ->
                        when (result) {
                            is Result.Success ->
                                _uiState.update {
                                    it.copy(user = result.data, isLoading = false, profileError = null)
                                }
                            is Result.Error ->
                                _uiState.update {
                                    it.copy(profileError = "Error: ${result.exception.message}", isLoading = false)
                                }
                            else -> {}
                        }
                    }
                }
        }

        override fun onCleared() {
            super.onCleared()
            profileJob?.cancel()
        }

        private fun loadCertificates() {
            viewModelScope.launch {
                when (val result = certificateRepository.getCertificatesForUser(uid)) {
                    is Result.Success ->
                        _uiState.update {
                            it.copy(certificates = result.data, certificatesError = null)
                        }
                    is Result.Error ->
                        _uiState.update {
                            it.copy(certificatesError = "Couldn't load certificates: ${result.exception.message}")
                        }
                    Result.Loading -> Unit
                }
            }
        }
    }
