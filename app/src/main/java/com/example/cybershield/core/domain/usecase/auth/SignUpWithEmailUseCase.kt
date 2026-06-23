package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SignUpResult(
    val user:                 FirebaseUser,
    val displayNameSet:       Boolean,
    val verificationEmailSent: Boolean,
)
class SignUpWithEmailUseCase @Inject constructor(
    private val auth: FirebaseAuth,
) {
    suspend operator fun invoke(
        email:    String,
        password: String,
        name:     String,
    ): Result<SignUpResult> =
        try {
            val authResult = auth
                .createUserWithEmailAndPassword(email, password)
                .await()
            val user = authResult.user
                ?: return Result.Error(
                    IllegalStateException("Account created but no user was returned")
                )
            val displayNameSet = try {
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                ).await()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }

            val verificationEmailSent = try {
                user.sendEmailVerification().await()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }

            Result.Success(SignUpResult(user, displayNameSet, verificationEmailSent))
        } catch (e: CancellationException) {
            throw e
        }catch (e: Exception) {
            Result.Error(e)
        }
}