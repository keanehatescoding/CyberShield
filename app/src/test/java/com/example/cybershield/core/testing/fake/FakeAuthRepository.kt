package com.example.cybershield.core.testing.fake

import com.example.cybershield.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.flow

class FakeAuthRepository : AuthRepository {
    var currentSessionToReturn: AuthRepository.AuthSession? = null

    override fun currentSession(): AuthRepository.AuthSession? = currentSessionToReturn

    override fun observeAuthState() = flow { emit(currentSessionToReturn) }

    override suspend fun register(
        email: String,
        password: String,
    ) = throw NotImplementedError("Not used by HomeViewModel")

    override suspend fun updateDisplayName(name: String) = throw NotImplementedError("Not used by HomeViewModel")

    override suspend fun deleteCurrentUser() = throw NotImplementedError("Not used by HomeViewModel")

    override suspend fun signIn(
        email: String,
        password: String,
    ) = throw NotImplementedError("Not used by HomeViewModel")

    override suspend fun resendVerificationEmail() = throw NotImplementedError("Not used by HomeViewModel")

    override suspend fun refreshEmailVerified() = throw NotImplementedError("Not used by HomeViewModel")

    override fun signOut() = throw NotImplementedError("Not used by HomeViewModel")
}
