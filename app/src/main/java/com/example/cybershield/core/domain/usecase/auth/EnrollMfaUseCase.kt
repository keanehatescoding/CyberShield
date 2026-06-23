package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorGenerator
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Takes the SMS code the user typed (or an auto-retrieved credential)
 * and finalizes phone enrollment as a second factor on the CURRENTLY
 * signed-in user. Combines "verify code" + "enroll" into one atomic step,
 * since Firebase does not expose these as separate operations.
 */
class EnrollMfaUseCase @Inject constructor(
    private val auth: FirebaseAuth,
) {
    suspend operator fun invoke(
        verificationId: String,
        smsCode:        String,
        displayName:    String = "My phone",
    ): Result<Unit> = enroll(
        PhoneAuthProvider.getCredential(verificationId, smsCode),
        displayName,
    )

    // Overload for the rare auto-retrieval case (onVerificationCompleted)
    suspend operator fun invoke(
        credential:  PhoneAuthCredential,
        displayName: String = "My phone",
    ): Result<Unit> = enroll(credential, displayName)

    private suspend fun enroll(
        credential:  PhoneAuthCredential,
        displayName: String,
    ): Result<Unit> =
        try {
            val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
            auth.currentUser
                ?.multiFactor
                ?.enroll(assertion, displayName)
                ?.await()
                ?: return Result.Error(Exception("No signed-in user to enroll"))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
}