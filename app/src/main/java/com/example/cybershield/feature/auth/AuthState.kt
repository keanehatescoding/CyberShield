package com.example.cybershield.feature.auth

sealed class AuthState {
    data object Resolving : AuthState()

    data class SignedOut(
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : AuthState()

    data class AwaitingEmailVerification(
        val email: String,
        val isResending: Boolean = false,
        val resendCooldownSeconds: Int = 0,
        val error: String? = null,
    ) : AuthState()

    data class Authenticated(
        val uid: String,
    ) : AuthState()
}
