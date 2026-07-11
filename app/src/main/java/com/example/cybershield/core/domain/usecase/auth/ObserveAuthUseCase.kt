package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Wraps the repository's auth-session access. Exposes both the one-shot
 * current session (used by AuthViewModel at init) and a live [observe] flow
 * (used by HomeViewModel so the profile loads as soon as the session is
 * restored), so ViewModels never depend on AuthRepository (or FirebaseAuth)
 * directly.
 */
class ObserveAuthStateUseCase
@Inject
constructor(
    private val authRepository: AuthRepository,
) {
    fun currentSession(): AuthRepository.AuthSession? = authRepository.currentSession()

    fun observe(): Flow<AuthRepository.AuthSession?> = authRepository.observeAuthState()
}
