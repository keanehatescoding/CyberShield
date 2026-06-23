package com.example.cybershield.feature.auth

import android.app.Activity
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.example.cybershield.core.domain.util.onError
import com.example.cybershield.core.domain.util.onSuccess
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.firebase.FirestoreAuthDataSource
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorGenerator
import com.google.firebase.auth.PhoneMultiFactorInfo
import java.util.concurrent.TimeUnit

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithEmailUseCase:  SignInWithEmailUseCase,
    private val signUpWithEmailUseCase:  SignUpWithEmailUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase:          SignOutUseCase,
    private val userRepository:   UserRepository,
    private val firebaseAuth:     FirebaseAuth,
    private val firebaseAuthDataSource: FirestoreAuthDataSource,
    val googleSignInHelper: GoogleSignInHelper,
) : ViewModel() {

    private var lastResendToken: PhoneAuthProvider.ForceResendingToken? = null
    // ── Auth state (drives navigation in NavigationRoot) ───────────────
    val authState: StateFlow<FirebaseUser?> = firebaseAuthDataSource
        .authStateChanges()
        .stateIn(
            scope         = viewModelScope,
            started       = SharingStarted.Eagerly,
            initialValue  = firebaseAuthDataSource.currentUser,
        )

    // ── Login screen UI state ──────────────────────────────────────────
    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    // ── Register screen UI state ───────────────────────────────────────
    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    private val _googleSignInState = MutableStateFlow(GoogleSignInUiState())
    val googleSignInState: StateFlow<GoogleSignInUiState> = _googleSignInState.asStateFlow()

    // ── One-time events (Snackbar, email verification notice) ─────────
    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    // ═══════════════════════════════════════════════════════════════════
    // Email sign-in
    // ═══════════════════════════════════════════════════════════════════
    fun signInWithEmail(email: String, password: String, activity: Activity) {
        if (!validateLoginInputs(email, password)) return
        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, error = null) }

            signInWithEmailUseCase(email.trim(), password)
                .onSuccess {
                    // authState emits non-null — NavigationRoot handles nav
                    // isLoading stays true to block button during transition
                }
                .onError { e ->
                    if (e is FirebaseAuthMultiFactorException) {
                        // ★ Check the type HERE, inside onError, where the exception
                        // actually arrives — this is the only place it ever will
                        handleMfaRequired(e,activity)
                    } else {
                        _loginState.update {
                            it.copy(
                                isLoading = false,
                                error = mapAuthError(e),
                            )
                        }
                    }
                }
        }
    }

    // Shared by signInWithEmail and linkGoogleToExistingAccount — both can
    // surface a second-factor challenge from Firebase and need to route to
    // MfaVerificationScreen the same way.
    private fun handleMfaRequired(e: FirebaseAuthMultiFactorException,activity: Activity) {
        val resolver = e.resolver
        _loginState.update {
            it.copy(isLoading = false, pendingMfaResolver = resolver)
        }
        sendMfaSmsCode(resolver,activity)
    }

    // ═══════════════════════════════════════════════════════════════════
// Called from MfaVerificationScreen when user submits the 6-digit code
// ═══════════════════════════════════════════════════════════════════
    fun verifyMfaCode(code: String) {
        val resolver       = _loginState.value.pendingMfaResolver ?: return
        val verificationId = _loginState.value.mfaVerificationId  ?: return

        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        completeMfaSignIn(resolver, credential)
    }

    private fun completeMfaSignIn(
        resolver:   MultiFactorResolver,
        credential: PhoneAuthCredential,
    ) {
        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, error = null) }
            try {
                val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
                resolver.resolveSignIn(assertion).await()

                // Success — authState now emits the signed-in user automatically
                _loginState.update {
                    it.copy(
                        isLoading           = false,
                        pendingMfaResolver  = null,
                        mfaCodeSent         = false,
                        mfaVerificationId   = null,
                    )
                }
            } catch (_: Exception) {
                _loginState.update {
                    it.copy(isLoading = false, error = "Incorrect code. Please try again.")
                }
            }
        }
    }

    fun cancelMfaSignIn() {
        _loginState.update {
            it.copy(pendingMfaResolver = null, mfaCodeSent = false, mfaVerificationId = null)
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

            when (val authResult =
                signUpWithEmailUseCase(email.trim(), password, name.trim())
            ) {
                is Result.Success -> {
                    val signUp = authResult.data
                    val user = signUp.user

                    when(val profileResult = userRepository.createUserProfile(
                        uid = user.uid,
                        displayName = name.trim(),
                        email = email.trim()
                    )){
                        is Result.Success -> {
                            if (signUp.verificationEmailSent) {
                                _events.send(AuthEvent.EmailVerificationSent)
                            } else {
                                _events.send(AuthEvent.Error(
                                    "Account created! We couldn't send the verification email — " +
                                            "tap 'Resend' on the next screen to try again."
                                ))
                                _events.send(AuthEvent.EmailVerificationSent)
                            }
                            _registerState.update { it.copy(isLoading = false) }
                        }
                        is Result.Error -> {
                            _registerState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Account created but profile setup failed. Please try signing in — we'll retry automatically.",
                                )
                            }
                        }
                        Result.Loading -> Unit
                    }

                }

                is Result.Error -> {
                    _registerState.update {
                        it.copy(
                            isLoading = false,
                            error = mapAuthError(authResult.exception)
                        )
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Google SSO — callable from both LoginScreen and RegisterScreen.
    // Drives _googleSignInState, NOT loginState/registerState, since either
    // screen can be the caller. AccountCollision and Error are broadcast as
    // one-time events on _events, which both screens already collect.
    // ═══════════════════════════════════════════════════════════════════
    fun signInWithGoogle(idToken: String, activity: Activity? = null) {
        viewModelScope.launch {
            _googleSignInState.update { it.copy(isLoading = true, error = null) }

            when (val result = signInWithGoogleUseCase(idToken)) {
                is Result.Success -> {
                    val user = result.data

                    try {
                        userRepository.createUserProfileIfNotExists(
                            uid = user.uid,
                            displayName = user.displayName ?: "CyberShield User",
                            email = user.email ?: "",
                            photoUrl = user.photoUrl?.toString()
                        )
                        // authState emits non-null — NavigationRoot handles nav.
                        // isLoading stays true to block the button during transition.
                    } catch (e: Exception) {
                        // Profile write failed after a successful auth — surface it
                        // instead of leaving isLoading stuck true with no feedback.
                        _googleSignInState.update {
                            it.copy(isLoading = false, error = mapAuthError(e))
                        }
                        _events.send(AuthEvent.Error(mapAuthError(e)))
                    }
                }

                is Result.Error -> {
                    val e = result.exception
                    when {
                        e is FirebaseAuthMultiFactorException && activity != null -> {
                            _googleSignInState.update { it.copy(isLoading = false) }
                            handleMfaRequired(e, activity)
                        }
                        e is FirebaseAuthUserCollisionException -> {
                            // Google email matches an existing email/password account.
                            // Surface the linking dialog instead of a generic error —
                            // this event is collected by both LoginScreen and
                            // RegisterScreen now, so it works regardless of which
                            // screen the user tapped "Continue with Google" from.
                            val email = e.email
                            val pendingCredential = GoogleAuthProvider.getCredential(idToken, null)
                            if (email != null) {
                                _googleSignInState.update { it.copy(isLoading = false) }
                                _events.send(
                                    AuthEvent.AccountCollision(
                                        email = email,
                                        googleCredential = pendingCredential,
                                    )
                                )
                            } else {
                                val msg = mapAuthError(e)
                                _googleSignInState.update { it.copy(isLoading = false, error = msg) }
                                _events.send(AuthEvent.Error(msg))
                            }
                        }
                        else -> {
                            val msg = mapAuthError(e)
                            _googleSignInState.update { it.copy(isLoading = false, error = msg) }
                            _events.send(AuthEvent.Error(msg))
                        }
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
        activity: Activity
    ) {
        viewModelScope.launch {
            _googleSignInState.update { it.copy(isLoading = true, error = null) }

            try {
                // Step 1 — sign in with existing email/password account
                firebaseAuth
                    .signInWithEmailAndPassword(email, password)
                    .await()

                // Step 2 — link Google credential to the now signed-in account
                firebaseAuthDataSource.currentUser
                    ?.linkWithCredential(googleCredential)
                    ?.await()

                // Both providers are now linked.
                // authState emits non-null → NavigationRoot navigates home.
                // No manual navigation needed.
                _googleSignInState.update { it.copy(isLoading = false) }

            } catch (_: FirebaseAuthInvalidCredentialsException) {
                // Wrong password entered in the link dialog
                _googleSignInState.update {
                    it.copy(isLoading = false, error = "Incorrect password. Please try again.")
                }
            } catch (e: FirebaseAuthMultiFactorException) {
                // Note: handleMfaRequired writes to _loginState, which is correct —
                // MFA verification always proceeds through MfaVerificationScreen,
                // which reads loginState regardless of which screen initiated
                // the original Google link attempt.
                _googleSignInState.update { it.copy(isLoading = false) }
                handleMfaRequired(e, activity)
            } catch (e: Exception) {
                _googleSignInState.update {
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
            try {
                firebaseAuth.sendPasswordResetEmail(email.trim()).await()
                _events.send(AuthEvent.PasswordResetSent(email.trim()))
            } catch (e: Exception) {
                _events.send(AuthEvent.Error(mapAuthError(e)))
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
        if (password.length < 8) {
            _loginState.update { it.copy(passwordError = "Password must be at least 8 characters") }
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
        if (password.length < 8) {
            _registerState.update { it.copy(passwordError = "Password must be at least 8 characters") }
            valid = false
        }
        if (password != confirmPassword) {
            _registerState.update { it.copy(confirmPasswordError = "Passwords do not match") }
            valid = false
        }
        return valid
    }

    fun resetLoginStateForDisplay() {
        _loginState.update {
            it.copy(
                isLoading = false,
                error = null,
                emailError = null,
                passwordError = null,
                pendingMfaResolver = null,
                mfaCodeSent = false,
                mfaVerificationId = null,
            )
        }
    }

    fun resetGoogleSignInState() {
        _googleSignInState.update { it.copy(isLoading = false, error = null) }
    }

    // ═══════════════════════════════════════════════════════════════════
// Maps Firebase exceptions to user-friendly strings
// ═══════════════════════════════════════════════════════════════════
    private fun mapAuthError(e: Throwable): String = when (e) {

        is FirebaseAuthInvalidCredentialsException -> "Incorrect password. Please try again."
        // Covers both ERROR_WRONG_PASSWORD and ERROR_INVALID_CREDENTIAL —
        // both genuinely mean "the credential you supplied is wrong," so a
        // single message for this class is accurate and simpler than trying
        // to distinguish them via errorCode for no real UX benefit.

        is FirebaseAuthInvalidUserException -> when (e.errorCode) {
            "ERROR_USER_DISABLED" -> "This account has been disabled. Contact support."
            else                      -> "No account found with this email."
            // else branch covers ERROR_USER_NOT_FOUND and any other code
            // Firebase might add to this exception type in the future
        }

        is FirebaseAuthUserCollisionException -> "This email is already registered. Try signing in."

        is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a moment and try again."

        is FirebaseNetworkException -> "No internet connection. Check your data and try again."

        is FirebaseAuthException -> {
            // ★ Catch-all for any OTHER FirebaseAuthException not explicitly
            // handled above. Still uses the stable .errorCode property —
            // never .message — even in this fallback branch.
            when (e.errorCode) {
                "ERROR_USER_TOKEN_EXPIRED" -> "Your session has expired. Please sign in again."
                else -> "Something went wrong. Please try again."
            }
        }

        else -> "Something went wrong. Please try again."
        // Genuinely unrelated exceptions — e.g. a NullPointerException from
        // elsewhere that got routed here by mistake — still get a safe,
        // generic fallback rather than crashing the error-mapping itself.
    }
    // Update sendMfaSmsCode() to capture the resend token from onCodeSent:
    private fun sendMfaSmsCode(resolver: MultiFactorResolver,activity: Activity) {
        val phoneFactor = resolver.hints
            .filterIsInstance<PhoneMultiFactorInfo>()
            .firstOrNull() ?: return

        val options = PhoneAuthOptions.Builder(firebaseAuth)
            .setMultiFactorSession(resolver.session)
            .setMultiFactorHint(phoneFactor)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    lastResendToken = token
                    _loginState.update {
                        it.copy(mfaCodeSent = true, mfaVerificationId = verificationId)
                    }
                }
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    completeMfaSignIn(resolver, credential)
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    _loginState.update {
                        it.copy(error = "Failed to send verification code. Try again.")
                    }
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun resendMfaSmsCode(activity: Activity) {
        val resolver = _loginState.value.pendingMfaResolver ?: return
        val phoneFactor = resolver.hints
            .filterIsInstance<PhoneMultiFactorInfo>()
            .firstOrNull() ?: return

        _loginState.update { it.copy(mfaCodeSent = false) }   // disables Verify button until new code arrives

        val optionsBuilder = PhoneAuthOptions.Builder(firebaseAuth)
            .setMultiFactorSession(resolver.session)
            .setMultiFactorHint(phoneFactor)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    lastResendToken = token
                    _loginState.update {
                        it.copy(mfaCodeSent = true, mfaVerificationId = verificationId)
                    }
                }
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    completeMfaSignIn(resolver, credential)
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    _loginState.update { it.copy(error = "Failed to resend code.") }
                }
            })

        // Pass the resend token so Firebase knows this is a re-send, not a fresh attempt
        lastResendToken?.let { optionsBuilder.setForceResendingToken(it) }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }
    fun resendEmailVerification() {
        val user = firebaseAuthDataSource.currentUser
        if (user == null) {
            viewModelScope.launch {
                _events.send(AuthEvent.Error("No signed-in user found. Please sign in again."))
            }
            return
        }

        viewModelScope.launch {
            try {
                user.sendEmailVerification().await()
                _events.send(AuthEvent.EmailVerificationSent)
            } catch (e: Exception) {
                _events.send(AuthEvent.Error(mapAuthError(e)))
            }
        }
    }
}