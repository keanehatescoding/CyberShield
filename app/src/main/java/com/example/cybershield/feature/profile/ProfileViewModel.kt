package com.example.cybershield.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.example.cybershield.core.domain.util.Result

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
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
            try {
                val snap = firestore
                    .collection("users").document(uid)
                    .collection("certificates")
                    .get().await()
                val certs = snap.documents.mapNotNull { doc ->
                    Certificate(
                        id         = doc.id,
                        quizTitle  = doc.getString("quizTitle") ?: "",
                        score      = doc.getLong("score")?.toInt() ?: 0,
                        datePassed = doc.getDate("completedAt"),
                        pdfUrl     = doc.getString("pdfUrl"),
                    )
                }
                _uiState.update { it.copy(certificates = certs) }
            } catch (_: Exception) {}
        }
    }
}

data class Certificate(
    val id:         String,
    val quizTitle:  String,
    val score:      Int,
    val datePassed: java.util.Date?,
    val pdfUrl:     String?,
)

data class ProfileUiState(
    val user:         User?           = null,
    val certificates: List<Certificate> = emptyList(),
    val isLoading:    Boolean          = true,
    val error:        String?          = null,
)