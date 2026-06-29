package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.repository.AuthRepository
import javax.inject.Inject

class SignOutUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) {
        operator fun invoke() = authRepository.signOut()
    }
