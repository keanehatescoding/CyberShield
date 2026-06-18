package com.example.cybershield.core.domain.usecase.auth


import com.example.cybershield.core.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val auth: FirebaseAuth,
) {
    suspend operator fun invoke(idToken: String): Result<FirebaseUser> =
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val user = auth
                .signInWithCredential(credential)
                .await()
                .user!!
            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
}
