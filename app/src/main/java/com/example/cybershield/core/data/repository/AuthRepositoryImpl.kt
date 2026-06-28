package com.example.cybershield.core.data.repository

import com.example.cybershield.core.firebase.FirebaseAuthDataSource
import com.example.cybershield.core.domain.model.AuthError
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.repository.AuthRepository.AuthSession
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val userRepository: UserRepository,
) : AuthRepository {

    override fun currentSession(): AuthSession? =
        authDataSource.currentUser?.toSession()

    override fun observeAuthState(): Flow<AuthSession?> =
        authDataSource.authStateChanges().map { it?.toSession() }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): Result<Unit> = try {
        val user = authDataSource.createUserWithEmailAndPassword(email, password)
            ?: return Result.Error(AuthError.Unknown())

        authDataSource.updateDisplayName(user, name)

        val profileResult = userRepository.createUserProfile(
            uid = user.uid,
            displayName = name,
            email = email,
            photoUrl = user.photoUrl?.toString(),
        )
        if (profileResult is Result.Error) {
            return Result.Error(AuthError.Unknown(profileResult.exception))
        }

        authDataSource.sendEmailVerification(user)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.toAuthError())
    }

    override suspend fun signIn(
        email: String,
        password: String,
    ): Result<AuthSession> = try {
        val user = authDataSource.signInWithEmailAndPassword(email, password)
            ?: return Result.Error(AuthError.Unknown())
        Result.Success(user.toSession())
    } catch (e: Exception) {
        Result.Error(e.toAuthError())
    }

    override suspend fun resendVerificationEmail(): Result<Unit> = try {
        val user = authDataSource.currentUser
            ?: return Result.Error(AuthError.Unknown())
        authDataSource.sendEmailVerification(user)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e.toAuthError())
    }

    override suspend fun refreshEmailVerified(): Result<Boolean> = try {
        val user = authDataSource.reloadCurrentUser()
            ?: return Result.Error(AuthError.Unknown())
        Result.Success(user.isEmailVerified)
    } catch (e: Exception) {
        Result.Error(e.toAuthError())
    }

    override fun signOut() = authDataSource.signOut()

    private fun FirebaseUser.toSession() = AuthSession(
        uid = uid,
        email = email,
        isEmailVerified = isEmailVerified,
    )

    /** Maps raw Firebase exceptions to typed, self-describing AuthError instances. */
    private fun Exception.toAuthError(): AuthError = when (this) {
        is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidCredentials
        is FirebaseAuthInvalidUserException -> AuthError.UserNotFound
        is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse
        is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
        is FirebaseNetworkException -> AuthError.NoNetwork
        else -> AuthError.Unknown(this)
    }
}