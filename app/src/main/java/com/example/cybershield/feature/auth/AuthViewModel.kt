package com.example.cybershield.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.usecase.auth.CheckEmailVerifiedUseCase
import com.example.cybershield.core.domain.usecase.auth.ObserveAuthStateUseCase
import com.example.cybershield.core.domain.usecase.auth.RegisterUseCase
import com.example.cybershield.core.domain.usecase.auth.ResendVerificationEmailUseCase
import com.example.cybershield.core.domain.usecase.auth.SignInUseCase
import com.example.cybershield.core.domain.usecase.auth.SignOutUseCase
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.sync.FcmTokenSyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val observeAuthState: ObserveAuthStateUseCase,
        private val registerUseCase: RegisterUseCase,
        private val signInUseCase: SignInUseCase,
        private val resendVerificationEmailUseCase: ResendVerificationEmailUseCase,
        private val checkEmailVerifiedUseCase: CheckEmailVerifiedUseCase,
        private val signOutUseCase: SignOutUseCase,
        private val fcmTokenSyncTrigger: FcmTokenSyncTrigger,
    ) : ViewModel() {
        private val _state = MutableStateFlow<AuthState>(AuthState.Resolving)
        val state: StateFlow<AuthState> = _state.asStateFlow()

        private var cooldownJob: Job? = null

        init {
            val session = observeAuthState.currentSession()
            _state.value =
                when {
                    session == null -> AuthState.SignedOut()
                    !session.isEmailVerified -> AuthState.AwaitingEmailVerification(email = session.email ?: "")
                    else -> AuthState.Authenticated(session.uid)
                }
            // Covers an already-authenticated cold start (e.g. a token issued
            // pre-login on a previous run never got attached — see
            // FcmTokenSyncTrigger). No-ops harmlessly otherwise.
            if (_state.value is AuthState.Authenticated) syncFcmToken()
        }

        fun register(
            name: String,
            email: String,
            password: String,
        ) {
            val current = _state.value as? AuthState.SignedOut ?: return
            _state.value = current.copy(isLoading = true, error = null)

            viewModelScope.launch {
                when (val result = registerUseCase(name, email, password)) {
                    is Result.Success -> _state.value = AuthState.AwaitingEmailVerification(email = email)
                    is Result.Error -> fail(result.exception.message ?: "Something went wrong. Please try again.")
                    is Result.Loading -> Unit
                }
            }
        }

        fun signIn(
            email: String,
            password: String,
        ) {
            val s = _state.value as? AuthState.SignedOut ?: return
            _state.value = s.copy(isLoading = true, error = null)

            viewModelScope.launch {
                when (val result = signInUseCase(email, password)) {
                    is Result.Success -> {
                        val session = result.data
                        _state.value =
                            if (session.isEmailVerified) {
                                AuthState.Authenticated(session.uid).also { syncFcmToken() }
                            } else {
                                AuthState.AwaitingEmailVerification(email = session.email ?: email)
                            }
                    }
                    is Result.Error ->
                        _state.value =
                            s.copy(
                                isLoading = false,
                                error = result.exception.message ?: "Something went wrong. Please try again.",
                            )
                    is Result.Loading -> Unit
                }
            }
        }

        fun resendVerificationEmail() {
            val s = _state.value as? AuthState.AwaitingEmailVerification ?: return
            _state.value = s.copy(isResending = true, error = null)

            viewModelScope.launch {
                when (val result = resendVerificationEmailUseCase()) {
                    is Result.Success -> {
                        _state.value = s.copy(isResending = false, resendCooldownSeconds = 60, error = null)
                        startCooldownTimer()
                    }
                    is Result.Error ->
                        _state.value =
                            s.copy(
                                isResending = false,
                                error = result.exception.message ?: "Couldn't resend the email. Please try again.",
                            )
                    is Result.Loading -> Unit
                }
            }
        }

        private fun startCooldownTimer() {
            cooldownJob?.cancel()
            cooldownJob =
                viewModelScope.launch {
                    for (remaining in 59 downTo 0) {
                        delay(1_000L.milliseconds)
                        val current = _state.value as? AuthState.AwaitingEmailVerification ?: break
                        _state.value = current.copy(resendCooldownSeconds = remaining)
                    }
                }
        }

        fun checkEmailVerified() {
            viewModelScope.launch {
                val session = observeAuthState.currentSession() ?: return@launch
                when (val result = checkEmailVerifiedUseCase()) {
                    is Result.Success ->
                        if (result.data) {
                            _state.value = AuthState.Authenticated(session.uid)
                            syncFcmToken()
                        }
                    is Result.Error -> Unit // stay, user can retry
                    is Result.Loading -> Unit
                }
            }
        }

        fun signOut() {
            signOutUseCase()
            _state.value = AuthState.SignedOut()
        }

        private fun fail(message: String) {
            _state.value = AuthState.SignedOut(error = message)
        }

        // Best-effort: a failure here shouldn't affect auth state, so it isn't
        // routed through Result/fail() — FcmTokenSyncTrigger logs and swallows
        // its own errors, and FcmTokenSyncWorker retries on the WorkManager side.
        private fun syncFcmToken() {
            viewModelScope.launch { fcmTokenSyncTrigger.syncCurrentToken() }
        }
    }
