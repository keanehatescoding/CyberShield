package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.model.AuthError
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import javax.inject.Inject

/**
 * Coordinates registration across [AuthRepository] (Firebase Auth account) and
 * [UserRepository] (Firestore profile). Neither repository knows about the other —
 * this use case owns the multi-step flow and its rollback/error handling, which is
 * what keeps AuthRepositoryImpl from depending on UserRepository.
 */
class RegisterUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val userRepository: UserRepository,
    ) {
        companion object {
            /**
             * Upper bound on the display name written to Firebase Auth and the
             * Firestore user profile. Without a bound here, an arbitrarily long
             * string can be submitted client-side, bloating the profile document
             * and any UI (leaderboards, certificates) that renders the name.
             */
            const val MAX_DISPLAY_NAME_LENGTH = 60
        }

        suspend operator fun invoke(
            name: String,
            email: String,
            password: String,
        ): Result<Unit> {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty() || trimmedName.length > MAX_DISPLAY_NAME_LENGTH) {
                return Result.Error(AuthError.InvalidDisplayName)
            }
            if (email.isBlank() || password.isBlank()) return Result.Error(AuthError.Unknown())

            val session =
                when (val result = authRepository.register(email, password)) {
                    is Result.Success -> result.data
                    is Result.Error -> return Result.Error(result.exception)
                    Result.Loading -> return Result.Error(AuthError.Unknown())
                }

            when (val nameResult = authRepository.updateDisplayName(trimmedName)) {
                is Result.Error -> {
                    // Auth account exists but is in a half-set-up state; roll it back
                    // rather than leaving an orphaned, name-less account behind.
                    authRepository.deleteCurrentUser()
                    return Result.Error(nameResult.exception)
                }
                Result.Loading -> {
                    authRepository.deleteCurrentUser()
                    return Result.Error(AuthError.Unknown())
                }
                is Result.Success -> Unit
            }

            val profileResult =
                userRepository.createUserProfile(
                    uid = session.uid,
                    displayName = trimmedName,
                    email = email,
                )
            if (profileResult is Result.Error) {
                // Clean up the Firebase Auth user to avoid orphaned accounts
                // (best-effort; if deletion fails, the user can still sign in
                // but will have no Firestore profile).
                authRepository.deleteCurrentUser()
                return Result.Error(AuthError.Unknown(profileResult.exception))
            }

            return when (val verifyResult = authRepository.resendVerificationEmail()) {
                is Result.Error -> Result.Error(verifyResult.exception)
                is Result.Success -> Result.Success(Unit)
                Result.Loading -> Result.Error(AuthError.Unknown())
            }
        }
    }
