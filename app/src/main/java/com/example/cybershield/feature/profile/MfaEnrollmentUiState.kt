
package com.example.cybershield.feature.profile

import com.google.firebase.auth.PhoneAuthProvider

data class MfaEnrollmentUiState(
    // ── Phone input ────────────────────────────────────────────────────
    val phoneNumber:    String  = "",

    // ── SMS code input ─────────────────────────────────────────────────
    val smsCode:        String  = "",

    // ── Flow control ───────────────────────────────────────────────────
    val isCodeSent:     Boolean = false,
    val isLoading:      Boolean = false,
    val isEnrolled:     Boolean = false,
    val error:          String? = null,

    // ── Held between send → confirm (needed by EnrollMfaUseCase) ───────
    val verificationId: String? = null,
    val resendToken:    PhoneAuthProvider.ForceResendingToken? = null,

    val requiresReauthToUnenroll: Boolean = false
) {
    // E.164 format check: must start with + and have at least 10 digits total
    // e.g. +254712345678 — good enough for client-side gating;
    // Firebase itself does the authoritative validation server-side
    val isPhoneValid: Boolean
        get() = phoneNumber.startsWith("+") && phoneNumber.length >= 10

    // Drives the "Send code" button — disabled while loading or already sent
    val canSendCode: Boolean
        get() = isPhoneValid && !isLoading && !isCodeSent

    // Drives the "Verify" button on the code-entry step
    val canConfirmCode: Boolean
        get() = isCodeSent && smsCode.length == 6 && !isLoading
}