package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Thin pass-through over AuthRepository.currentSession().
 * Exists so ViewModels never depend on AuthRepository (or FirebaseAuth) directly.
 */
class GetCurrentSessionUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) {
        operator fun invoke(): AuthRepository.AuthSession? = authRepository.currentSession()
    }
