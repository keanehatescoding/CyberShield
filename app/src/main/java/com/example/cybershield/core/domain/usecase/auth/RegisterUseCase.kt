package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.model.AuthError
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.util.Result
import javax.inject.Inject

class RegisterUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) {
        suspend operator fun invoke(
            name: String,
            email: String,
            password: String,
        ): Result<Unit> {
            if (name.isBlank()) return Result.Error(AuthError.Unknown())
            if (email.isBlank() || password.isBlank()) return Result.Error(AuthError.Unknown())
            return authRepository.register(name, email, password)
        }
    }
