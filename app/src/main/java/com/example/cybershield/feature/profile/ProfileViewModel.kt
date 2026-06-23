package com.example.cybershield.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.CertificateRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.example.cybershield.core.domain.model.Certificate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.cybershield.core.domain.util.Result

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    private val  certificateRepository: CertificateRepository,
) : ViewModel() {

    private val uid get() = firebaseAuth.currentUser?.uid ?: ""

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        loadCertificates()
    }


    private fun loadProfile() {
        viewModelScope.launch {
            userRepository.getUserProfile(uid).collect { result ->
                when (result) {
                    is Result.Success -> _uiState.update {
                        it.copy(user = result.data, isLoading = false)
                    }
                    is Result.Error   -> _uiState.update {
                        it.copy(error = result.exception.message, isLoading = false)
                    }
                    else -> {}
                }
            }
        }
    }
    private fun loadCertificates() {
        viewModelScope.launch {
            when (val result = certificateRepository.getCertificatesForUser(uid)) {
                is Result.Success -> _uiState.update {
                    it.copy(certificates = result.data)
                }
                is Result.Error -> _uiState.update {
                    // ★ ALSO fixes the original empty catch(_: Exception){} —
                    // failures are now visible instead of silently discarded
                    it.copy(error = "Couldn't load certificates: ${result.exception.message}")
                }
                Result.Loading -> Unit
            }
        }
    }
}

data class ProfileUiState(
    val user:         User?           = null,
    val certificates: List<Certificate> = emptyList(),
    val isLoading:    Boolean          = true,
    val error:        String?          = null,
)