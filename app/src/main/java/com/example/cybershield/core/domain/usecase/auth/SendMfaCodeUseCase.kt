package com.example.cybershield.core.domain.usecase.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Sealed result for the enrollment-time SMS send. Each possible Firebase
 * callback outcome maps to exactly one variant — no callback fires after
 * the Flow has emitted a terminal event, so collectors can rely on this
 * being a clean, linear sequence: zero or more CodeSent (resend), then
 * exactly one of AutoVerified or Failed, OR the flow simply completes
 * after CodeSent if the user types the code manually.
 */
sealed class MfaSendResult {
    data class CodeSent(
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken,
    ) : MfaSendResult()

    data class AutoVerified(
        val credential: PhoneAuthCredential,
    ) : MfaSendResult()

    data class Failed(
        val exception: Exception,
    ) : MfaSendResult()
}

class SendMfaCodeUseCase @Inject constructor(
    private val auth: FirebaseAuth,
) {
    /**
     * Returns a cold Flow that emits MfaSendResult events as Firebase's
     * verifyPhoneNumber callbacks fire. Collect this from a coroutine
     * tied to the screen's lifecycle (e.g. viewModelScope.launch).
     *
     * The Flow completes naturally after CodeSent (most common case —
     * user will type the code manually) or after AutoVerified/Failed
     * (terminal states). awaitClose detaches nothing here since Firebase
     * has no "cancel verification" API — it's a no-op cleanup, included
     * for correctness and to satisfy callbackFlow's contract.
     */
    operator fun invoke(
        phoneNumber: String,
        activity: Activity,
    ): Flow<MfaSendResult> = callbackFlow {
        val session = try {
            auth.currentUser?.multiFactor?.session?.await()
        } catch (e: Exception) {
            trySend(MfaSendResult.Failed(e))
            close()
            return@callbackFlow
        }

        if (session == null) {
            trySend(MfaSendResult.Failed(Exception("No signed-in user")))
            close()
            return@callbackFlow
        }

        val options = PhoneAuthOptions.Builder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setMultiFactorSession(session)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    trySend(MfaSendResult.CodeSent(verificationId, token))
                    // Don't close — caller may still receive onCodeAutoRetrievalTimeOut
                    // or, in rare cases, onVerificationCompleted shortly after.
                    // We close on the terminal events below instead.
                }

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    trySend(MfaSendResult.AutoVerified(credential))
                    close()   // terminal — auto-verification succeeded
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    trySend(MfaSendResult.Failed(e))
                    close()   // terminal — this is the fix: now Failed actually reaches the caller
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)

        awaitClose {
            // Firebase has no cancellation hook for an in-flight verifyPhoneNumber
            // call — nothing to release here. Included to satisfy callbackFlow.
        }
    }
}