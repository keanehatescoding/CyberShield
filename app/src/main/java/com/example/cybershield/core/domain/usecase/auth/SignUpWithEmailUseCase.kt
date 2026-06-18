package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SignUpWithEmailUseCase @Inject constructor(
    private val auth: FirebaseAuth,
) {
    suspend operator fun invoke(
        email:    String,
        password: String,
        name:     String,
    ): Result<FirebaseUser> =
        try {
            val user = auth
                .createUserWithEmailAndPassword(email, password)
                .await()
                .user!!

            // Set display name immediately so it's available on first sign-in
            user.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
            ).await()

            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
}