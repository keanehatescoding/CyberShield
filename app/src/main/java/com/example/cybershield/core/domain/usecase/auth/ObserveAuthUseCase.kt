package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Wraps the repository's current-session snapshot. Kept as a use case (rather than
 * calling the repository directly from the ViewModel) so the "what counts as signed in /
 * awaiting verification / authenticated" decision stays testable independent of the VM.
 */
class ObserveAuthStateUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) {
        fun currentSession(): AuthRepository.AuthSession? = authRepository.currentSession()
    }
