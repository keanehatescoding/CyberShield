package com.example.cybershield.core.domain.repository

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    // ── Read ───────────────────────────────────────────────────────────

    /** Returns a real-time Flow of the current user's profile. */
    fun getUserProfile(uid: String): Flow<Result<User>>

    /** One-shot fetch of current user profile. */
    suspend fun getUserProfileOnce(uid: String): Result<User>

    // ── Write ──────────────────────────────────────────────────────────

    /** Creates profile on first registration. */
    suspend fun createUserProfile(
        uid:         String,
        displayName: String,
        email:       String,
        photoUrl:    String? = null,
    ): Result<Unit>

    /** Creates profile if missing — safe to call on every Google SSO. */
    suspend fun createUserProfileIfNotExists(
        uid:         String,
        displayName: String,
        email:       String,
        photoUrl:    String? = null,
    ): Result<Unit>

    /** Atomically adds XP — uses FieldValue.increment server-side. */
    suspend fun addXp(uid: String, points: Int): Result<Unit>

    /** Awards a badge — uses FieldValue.arrayUnion, idempotent. */
    suspend fun awardBadge(uid: String, badge: String): Result<Unit>

    /** Marks a quiz as completed. */
    suspend fun markQuizCompleted(uid: String, quizId: String): Result<Unit>

    /** Marks a module as completed. */
    suspend fun markModuleCompleted(uid: String, moduleId: String): Result<Unit>

    /** Saves the FCM token for push notifications. */
    suspend fun updateFcmToken(uid: String, token: String): Result<Unit>

    /** Updates last sign-in timestamp. */
    suspend fun updateLastSignedIn(uid: String): Result<Unit>
    suspend fun saveCertificate(certificate: Certificate): Result<Unit>
}