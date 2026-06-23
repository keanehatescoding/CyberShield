package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SignInWithEmailUseCase @Inject constructor(
    private val auth: FirebaseAuth,
) {
    suspend operator fun invoke(email: String, password: String): Result<FirebaseUser> =
        try {
            val authResult = auth
                .signInWithEmailAndPassword(email, password)
                .await()
            val user = authResult.user
                ?: return Result.Error(
                    IllegalStateException("Sign-in succeeded but no user was returned")
                )
            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
}