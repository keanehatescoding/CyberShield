package com.example.cybershield.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.usecase.auth.SignInWithEmailUseCase
import com.example.cybershield.core.domain.usecase.auth.SignUpWithEmailUseCase
import com.example.cybershield.core.domain.usecase.auth.SignInWithGoogleUseCase
import com.example.cybershield.core.domain.usecase.auth.SignOutUseCase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.example.cybershield.core.domain.util.onError
import com.example.cybershield.core.domain.util.onSuccess
import com.example.cybershield.core.domain.util.Result

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithEmailUseCase:  SignInWithEmailUseCase,
    private val signUpWithEmailUseCase:  SignUpWithEmailUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase:          SignOutUseCase,
    private val userRepository:   UserRepository,
    private val firebaseAuth:     FirebaseAuth,
    val googleSignInHelper: GoogleSignInHelper,
) : ViewModel() {

    // ── Auth state (drives navigation in NavigationRoot) ───────────────
    fun FirebaseAuth.authStateChanges(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener{ auth ->
            trySend(auth.currentUser)
        }
        addAuthStateListener(listener)
        awaitClose { removeAuthStateListener (listener) }
    }
    val authState: StateFlow<FirebaseUser?> = firebaseAuth
        .authStateChanges()
        .stateIn(
            scope         = viewModelScope,
            started       = SharingStarted.Eagerly,
            initialValue  = firebaseAuth.currentUser,
        )

    // ── Login screen UI state ──────────────────────────────────────────
    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    // ── Register screen UI state ───────────────────────────────────────
    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    // ── One-time events (Snackbar, email verification notice) ─────────
    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    // ═══════════════════════════════════════════════════════════════════
    // Email sign-in
    // ═══════════════════════════════════════════════════════════════════
    fun signInWithEmail(email: String, password: String) {
        if (!validateLoginInputs(email, password)) return

        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, error = null) }

            signInWithEmailUseCase(email.trim(), password)
                .onSuccess {
                    // authState emits non-null — NavigationRoot handles nav
                    // isLoading stays true to block button during transition
                }
                .onError { e ->
                    _loginState.update {
                        it.copy(
                            isLoading = false,
                            error = mapAuthError(e),
                        )
                    }
                }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Email registration
    // ═══════════════════════════════════════════════════════════════════
    fun signUpWithEmail(
        name:            String,
        email:           String,
        password:        String,
        confirmPassword: String,
    ) {
        if (!validateRegisterInputs(name, email, password, confirmPassword)) return

        viewModelScope.launch {
            _registerState.update { it.copy(isLoading = true, error = null) }

            when (val result =
                signUpWithEmailUseCase(email.trim(), password, name.trim())
            ) {
                is Result.Success -> {
                    val user = result.data

                    userRepository.createUserProfile(
                        uid = user.uid,
                        displayName = name.trim(),
                        email = email.trim()
                    )

                    _events.send(AuthEvent.EmailVerificationSent)
                    _registerState.update { it.copy(isLoading = false) }
                }

                is Result.Error -> {
                    _registerState.update {
                        it.copy(
                            isLoading = false,
                            error = mapAuthError(result.exception)
                        )
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
// Google SSO
// ═══════════════════════════════════════════════════════════════════
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, error = null) }

            when (val result = signInWithGoogleUseCase(idToken)) {
                is Result.Success -> {
                    val user = result.data

                    userRepository.createUserProfileIfNotExists(
                        uid = user.uid,
                        displayName = user.displayName ?: "CyberShield User",
                        email = user.email ?: "",
                        photoUrl = user.photoUrl?.toString()
                    )
                }

                is Result.Error -> {
                    _loginState.update {
                        it.copy(
                            isLoading = false,
                            error = mapAuthError(result.exception)
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }
    fun linkGoogleToExistingAccount(
        email:            String,
        password:         String,
        googleCredential: AuthCredential,   // the GoogleAuthProvider credential from the collision
    ) {
        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, error = null) }

            try {
                // Step 1 — sign in with existing email/password account
                firebaseAuth
                    .signInWithEmailAndPassword(email, password)
                    .await()

                // Step 2 — link Google credential to the now signed-in account
                firebaseAuth.currentUser
                    ?.linkWithCredential(googleCredential)
                    ?.await()

                // Both providers are now linked.
                // authState emits non-null → NavigationRoot navigates home.
                // No manual navigation needed.

            } catch (_: FirebaseAuthInvalidCredentialsException) {
                // Wrong password entered in the link dialog
                _loginState.update {
                    it.copy(isLoading = false, error = "Incorrect password. Please try again.")
                }
            } catch (e: Exception) {
                _loginState.update {
                    it.copy(isLoading = false, error = mapAuthError(e))
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
// Sign out
// ═══════════════════════════════════════════════════════════════════
    fun signOut() {
        viewModelScope.launch {
            signOutUseCase.invoke()
            // authState emits null — NavigationRoot routes to LoginScreen
        }
    }

    // ═══════════════════════════════════════════════════════════════════
// Password reset
// ═══════════════════════════════════════════════════════════════════
    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _loginState.update { it.copy(emailError = "Enter your email first") }
            return
        }
        viewModelScope.launch {
            firebaseAuth
                .sendPasswordResetEmail(email.trim())
                .addOnSuccessListener {
                    viewModelScope.launch {
                        _events.send(AuthEvent.PasswordResetSent(email.trim()))
                    }
                }
                .addOnFailureListener { e ->
                    viewModelScope.launch {
                        _events.send(AuthEvent.Error(mapAuthError(e)))
                    }
                }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
// Field-level validation — updates state with inline errors
// ═══════════════════════════════════════════════════════════════════
    fun onEmailChange(value: String) =
        _loginState.update { it.copy(email = value, emailError = null, error = null) }

    fun onPasswordChange(value: String) =
        _loginState.update { it.copy(password = value, passwordError = null, error = null) }

    fun onRegisterNameChange(value: String) =
        _registerState.update { it.copy(name = value, nameError = null) }

    fun onRegisterEmailChange(value: String) =
        _registerState.update { it.copy(email = value, emailError = null, error = null) }

    fun onRegisterPasswordChange(value: String) =
        _registerState.update { it.copy(password = value, passwordError = null) }

    fun onRegisterConfirmPasswordChange(value: String) =
        _registerState.update { it.copy(confirmPassword = value, confirmPasswordError = null) }

    // ═══════════════════════════════════════════════════════════════════
// Input validation
// ═══════════════════════════════════════════════════════════════════
    private fun validateLoginInputs(email: String, password: String): Boolean {
        var valid = true
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _loginState.update { it.copy(emailError = "Enter a valid email address") }
            valid = false
        }
        if (password.length < 6) {
            _loginState.update { it.copy(passwordError = "Password must be at least 6 characters") }
            valid = false
        }
        return valid
    }

    private fun validateRegisterInputs(
        name: String, email: String,
        password: String, confirmPassword: String,
    ): Boolean {
        var valid = true
        if (name.isBlank()) {
            _registerState.update { it.copy(nameError = "Enter your full name") }
            valid = false
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _registerState.update { it.copy(emailError = "Enter a valid email address") }
            valid = false
        }
        if (password.length < 6) {
            _registerState.update { it.copy(passwordError = "Password must be at least 6 characters") }
            valid = false
        }
        if (password != confirmPassword) {
            _registerState.update { it.copy(confirmPasswordError = "Passwords do not match") }
            valid = false
        }
        return valid
    }

    // ═══════════════════════════════════════════════════════════════════
// Maps Firebase exceptions to user-friendly strings
// ═══════════════════════════════════════════════════════════════════
    private fun mapAuthError(e: Throwable): String = when {
        e.message?.contains("ERROR_WRONG_PASSWORD")         == true ->
            "Incorrect password. Please try again."
        e.message?.contains("ERROR_USER_NOT_FOUND")          == true ->
            "No account found with this email."
        e.message?.contains("ERROR_EMAIL_ALREADY_IN_USE")    == true ->
            "This email is already registered. Try signing in."
        e.message?.contains("ERROR_INVALID_CREDENTIAL")      == true ->
            "Invalid email or password."
        e.message?.contains("ERROR_NETWORK_REQUEST_FAILED")  == true ->
            "No internet connection. Check your data and try again."
        e.message?.contains("ERROR_TOO_MANY_REQUESTS")       == true ->
            "Too many attempts. Please wait a moment and try again."
        e.message?.contains("ERROR_USER_DISABLED")           == true ->
            "This account has been disabled. Contact support."
        else -> "Something went wrong. Please try again."
    }
}