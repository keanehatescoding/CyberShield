package com.example.cybershield.feature.auth

sealed class AuthState {
    data object Resolving : AuthState()

    data class SignedOut(
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : AuthState()

    data class AwaitingEmailVerification(
        // The uid this verification wait is for — checkEmailVerified()'s result
        // is only ever applied if it still matches this, so a stale in-flight
        // check for a previous account can't authenticate a different one that
        // becomes AwaitingEmailVerification in the meantime.
        val uid: String,
        val email: String,
        val isResending: Boolean = false,
        val resendCooldownSeconds: Int = 0,
        val error: String? = null,
    ) : AuthState()

    data class Authenticated(
        val uid: String,
    ) : AuthState()
}
