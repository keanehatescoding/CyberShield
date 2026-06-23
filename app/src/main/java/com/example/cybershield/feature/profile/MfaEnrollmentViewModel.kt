
package com.example.cybershield.feature.profile

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.usecase.auth.EnrollMfaUseCase
import com.example.cybershield.core.domain.usecase.auth.MfaSendResult
import com.example.cybershield.core.domain.usecase.auth.SendMfaCodeUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.feature.auth.AuthEvent
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneMultiFactorInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class MfaEnrollmentViewModel @Inject constructor(
    private val firebaseAuth:       FirebaseAuth,
    private val sendMfaCodeUseCase: SendMfaCodeUseCase,
    private val enrollMfaUseCase:   EnrollMfaUseCase,
) : ViewModel() {

    // ── UI state ───────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(MfaEnrollmentUiState())
    val uiState: StateFlow<MfaEnrollmentUiState> = _uiState.asStateFlow()

    // ── One-time events ─────────────────────────────────────────────────
    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    fun requestUnenroll() {
        _uiState.update { it.copy(requiresReauthToUnenroll = true) }
    }
    fun cancelUnenrollRequest() {
        _uiState.update { it.copy(requiresReauthToUnenroll = false, error = null) }
    }
    fun confirmUnenrollWithPassword(password: String) {
        val user  = firebaseAuth.currentUser ?: return
        val email = user.email ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val credential = EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential).await()

                // Only reachable if reauthenticate() succeeded
                val factor = user.multiFactor.enrolledFactors.firstOrNull()
                if (factor != null) {
                    user.multiFactor.unenroll(factor).await()
                }

                _uiState.value = MfaEnrollmentUiState()  // reset — isEnrolled = false

            } catch (_: FirebaseAuthInvalidCredentialsException) {
                // Wrong password entered — do NOT unenroll, surface a clear error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Incorrect password. Two-factor authentication was not disabled.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Couldn't verify your identity. Please try again.")
                }
            }
        }
    }
    init {
        // Check if the user already has a phone factor enrolled
        val alreadyEnrolled = firebaseAuth.currentUser
            ?.multiFactor?.enrolledFactors
            ?.any { it is PhoneMultiFactorInfo } ?: false
        _uiState.update { it.copy(isEnrolled = alreadyEnrolled) }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Field updates
    // ═══════════════════════════════════════════════════════════════════
    fun onPhoneNumberChanged(value: String) =
        _uiState.update { it.copy(phoneNumber = value, error = null) }

    fun onSmsCodeChanged(value: String) =
        _uiState.update { it.copy(smsCode = value, error = null) }

    // ═══════════════════════════════════════════════════════════════════
    // Step 1 — send verification code to the phone being enrolled
    // ═══════════════════════════════════════════════════════════════════
    fun sendEnrollmentCode(activity: Activity) {
        val phone = _uiState.value.phoneNumber
        if (!_uiState.value.isPhoneValid) {
            _uiState.update {
                it.copy(error = "Enter a valid phone number with country code, e.g. +254712345678")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            sendMfaCodeUseCase(phone, activity).collect { result ->
                when (result) {
                    is MfaSendResult.CodeSent -> {
                        _uiState.update {
                            it.copy(
                                isLoading      = false,
                                isCodeSent     = true,
                                verificationId = result.verificationId,
                                resendToken    = result.resendToken,
                            )
                        }
                    }
                    is MfaSendResult.AutoVerified -> {
                        finishEnrollment(result.credential)
                    }
                    is MfaSendResult.Failed -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error     = result.exception.message ?: "Failed to send code",
                            )
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Step 2 — user types the 6-digit code, finalize enrollment
    // ═══════════════════════════════════════════════════════════════════
    fun confirmEnrollment() {
        val verificationId = _uiState.value.verificationId ?: return
        val code           = _uiState.value.smsCode
        if (code.length != 6) {
            _uiState.update { it.copy(error = "Enter the 6-digit code") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = enrollMfaUseCase(verificationId, code, "My phone")) {
                is Result.Success -> onEnrollmentSuccess()
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Incorrect code. Please try again.")
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    // ── Auto-retrieval path — rare, fires from sendEnrollmentCode() ────
    private fun finishEnrollment(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = enrollMfaUseCase(credential, "My phone")) {
                is Result.Success -> onEnrollmentSuccess()
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Could not verify automatically. Please try again.")
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    private suspend fun onEnrollmentSuccess() {
        _uiState.update {
            it.copy(isLoading = false, isEnrolled = true, isCodeSent = false, smsCode = "")
        }
        _events.send(AuthEvent.MfaEnrollmentSuccess)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Resend code — reuses the same Flow-based send path
    // ═══════════════════════════════════════════════════════════════════
    fun resendCode(activity: Activity) {
        _uiState.update { it.copy(isCodeSent = false, smsCode = "") }
        sendEnrollmentCode(activity)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Cancel mid-flow — e.g. user taps "Change number" or navigates back
    // ═══════════════════════════════════════════════════════════════════
    fun resetEnrollmentFlow() {
        _uiState.update {
            it.copy(
                isCodeSent     = false,
                smsCode        = "",
                error          = null,
                verificationId = null,
                resendToken    = null,
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Unenroll — disable 2FA from the Security settings screen
    // ═══════════════════════════════════════════════════════════════════
    fun unenroll() {
        viewModelScope.launch {
            val factor = firebaseAuth.currentUser
                ?.multiFactor?.enrolledFactors?.firstOrNull() ?: return@launch

            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                firebaseAuth.currentUser?.multiFactor
                    ?.unenroll(factor)?.await()
                _uiState.value = MfaEnrollmentUiState()  // reset to default — isEnrolled = false
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to disable two-factor authentication")
                }
            }
        }
    }

}