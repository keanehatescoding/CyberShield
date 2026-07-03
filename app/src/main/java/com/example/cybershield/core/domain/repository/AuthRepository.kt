package com.example.cybershield.core.domain.repository

import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for authentication. No Firebase types leak past this boundary —
 * use cases and the ViewModel only ever see [AuthSession] / [com.example.cybershield.core.domain.model.AuthError]
 * (the latter arrives inside Result.Error.exception).
 */
interface AuthRepository {
    /** Null uid means "no signed-in user". */
    data class AuthSession(
        val uid: String,
        val email: String?,
        val isEmailVerified: Boolean,
    )

    /** Current session snapshot, or null if signed out. Synchronous, no I/O. */
    fun currentSession(): AuthSession?

    /** Emits whenever Firebase's auth state changes (sign in/out). */
    fun observeAuthState(): Flow<AuthSession?>

    /** Creates the Firebase Auth account only. No Firestore profile is touched here —
     * that's [com.example.cybershield.core.domain.usecase.auth.RegisterUseCase]'s job. */
    suspend fun register(
        email: String,
        password: String,
    ): Result<AuthSession>

    /** Sets the Firebase Auth display name for the current user. */
    suspend fun updateDisplayName(name: String): Result<Unit>

    /** Deletes the current Firebase Auth user. Used to roll back a registration
     * when Firestore profile creation fails, avoiding an orphaned auth-only account. */
    suspend fun deleteCurrentUser(): Result<Unit>

    suspend fun signIn(
        email: String,
        password: String,
    ): Result<AuthSession>

    suspend fun resendVerificationEmail(): Result<Unit>

    /** Reloads the current user from Firebase and returns the fresh verification status. */
    suspend fun refreshEmailVerified(): Result<Boolean>

    fun signOut()
}