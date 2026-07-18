package com.example.cybershield.core.domain.repository

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
        uid: String,
        displayName: String,
        email: String,
        photoUrl: String? = null,
    ): Result<Unit>

    /** Creates profile if missing — safe to call on every Google SSO. */
    suspend fun createUserProfileIfNotExists(
        uid: String,
        displayName: String,
        email: String,
        photoUrl: String? = null,
    ): Result<Unit>

    /**
     * Server-authoritative module completion: marks the module completed
     * and awards its xpReward atomically via the completeModuleFn callable.
     *
     * This replaces the old markModuleCompleted() + addXp() pair. Those were
     * plain client Firestore writes gated only by `auth.uid == userId` —
     * nothing validated that the `points` passed to addXp actually matched
     * a real module's xpReward, so any authenticated client could award
     * itself arbitrary XP (and, via the old client-writable leaderboard
     * mirror, top the public leaderboard with it). The client never writes
     * xp or completedModules directly anymore — see firestore.rules.
     */
    suspend fun completeModule(
        uid: String,
        moduleId: String,
    ): Result<ModuleCompleteResult>

    /** Saves the FCM token for push notifications. */
    suspend fun updateFcmToken(
        uid: String,
        token: String,
    ): Result<Unit>

    /** Updates last sign-in timestamp. */
    suspend fun updateLastSignedIn(uid: String): Result<Unit>
}

/** Server-computed outcome of [UserRepository.completeModule]. */
data class ModuleCompleteResult(
    val alreadyCompleted: Boolean,
    val xpEarned: Int,
)
