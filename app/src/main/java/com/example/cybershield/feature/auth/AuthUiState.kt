package com.example.cybershield.feature.auth

import com.google.firebase.auth.AuthCredential

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val error: String? = null
)
{
    // Derived — button only enabled when both fields are non-blank
    val isSignInEnabled: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}
data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val error: String? = null
) {
    val isRegisterEnabled: Boolean
        get() = name.isNotBlank() &&
                email.isNotBlank() &&
                password.isNotBlank() &&
                confirmPassword.isNotBlank() &&
                password.length >= 8 &&
                !isLoading
}
sealed class AuthEvent {
    data object EmailVerificationSent: AuthEvent()
    data class PasswordResetSent(val email: String) : AuthEvent()
    data class Error(val message: String): AuthEvent()
    data class AccountCollision(
        val email: String,
        val googleCredential: AuthCredential,
        ): AuthEvent()
}