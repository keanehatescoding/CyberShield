package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.util.Result
import javax.inject.Inject

class ResendVerificationEmailUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> = authRepository.resendVerificationEmail()
    }
