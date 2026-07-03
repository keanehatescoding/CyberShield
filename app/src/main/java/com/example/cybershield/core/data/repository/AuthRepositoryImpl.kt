package com.example.cybershield.core.data.repository

import com.example.cybershield.core.domain.model.AuthError
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.repository.AuthRepository.AuthSession
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.firebase.FirebaseAuthDataSource
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl
@Inject
constructor(
    private val authDataSource: FirebaseAuthDataSource,
) : AuthRepository {
    override fun currentSession(): AuthSession? = authDataSource.currentUser?.toSession()

    override fun observeAuthState(): Flow<AuthSession?> = authDataSource.authStateChanges().map { it?.toSession() }

    override suspend fun register(
        email: String,
        password: String,
    ): Result<AuthSession> =
        try {
            val user =
                authDataSource.createUserWithEmailAndPassword(email, password)
                    ?: return Result.Error(AuthError.Unknown())
            Result.Success(user.toSession())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.toAuthError())
        }

    override suspend fun updateDisplayName(name: String): Result<Unit> =
        try {
            val user =
                authDataSource.currentUser
                    ?: return Result.Error(AuthError.Unknown())
            authDataSource.updateDisplayName(user, name)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.toAuthError())
        }

    override suspend fun deleteCurrentUser(): Result<Unit> =
        try {
            authDataSource.deleteCurrentUser()
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.toAuthError())
        }

    override suspend fun signIn(
        email: String,
        password: String,
    ): Result<AuthSession> =
        try {
            val user =
                authDataSource.signInWithEmailAndPassword(email, password)
                    ?: return Result.Error(AuthError.Unknown())
            Result.Success(user.toSession())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.toAuthError())
        }

    override suspend fun resendVerificationEmail(): Result<Unit> =
        try {
            val user =
                authDataSource.currentUser
                    ?: return Result.Error(AuthError.Unknown())
            authDataSource.sendEmailVerification(user)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.toAuthError())
        }

    override suspend fun refreshEmailVerified(): Result<Boolean> =
        try {
            val user =
                authDataSource.reloadCurrentUser()
                    ?: return Result.Error(AuthError.Unknown())
            Result.Success(user.isEmailVerified)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.toAuthError())
        }

    override fun signOut() = authDataSource.signOut()

    private fun FirebaseUser.toSession() =
        AuthSession(
            uid = uid,
            email = email,
            isEmailVerified = isEmailVerified,
        )

    /** Maps raw Firebase exceptions to typed, self-describing AuthError instances. */
    private fun Exception.toAuthError(): AuthError =
        when (this) {
            is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidCredentials
            is FirebaseAuthInvalidUserException -> AuthError.UserNotFound
            is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse
            is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
            is FirebaseNetworkException -> AuthError.NoNetwork
            else -> AuthError.Unknown(this)
        }
}