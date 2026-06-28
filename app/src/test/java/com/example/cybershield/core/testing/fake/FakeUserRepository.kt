package com.example.cybershield.core.testing.fake

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

class FakeUserRepository : UserRepository {
    // ── Mutable test state — inspect these in assertions ──────────────
    var fakeUser: User = User(
        uid = "test-uid",
        displayName = "Test User",
        email = "test@cybershield.com",
    )
    val savedCertificates  = mutableListOf<Certificate>()
    val awardedBadges      = mutableListOf<String>()
    val completedQuizIds   = mutableListOf<String>()
    val completedModuleIds = mutableListOf<String>()
    var totalXpAdded       = 0

    // ── getUserProfile() override controls ─────────────────────────────
    // Default (null) preserves the original behavior exactly: a fixed one-shot
    // flowOf(Result.Success(fakeUser)). Set this to drive Loading/Error states,
    // or to test multi-emission scenarios (e.g. a profile-repair retry path)
    // alongside emitUserProfile().
    var userProfileResult: Result<User>? = null
    private val userProfileFlow = MutableSharedFlow<Result<User>>(replay = 1)

    // ── createUserProfileIfNotExists() call tracking ────────────────────
    var createUserProfileIfNotExistsResult: Result<Unit> = Result.Success(Unit)
    var createUserProfileIfNotExistsCallCount = 0
    var lastCreateUserProfileIfNotExistsArgs: CreateProfileArgs? = null

    data class CreateProfileArgs(
        val uid: String,
        val displayName: String,
        val email: String,
        val photoUrl: String?,
    )

    // ── Read ──────────────────────────────────────────────────────────
    override fun getUserProfile(uid: String): Flow<Result<User>> {
        val override =
            userProfileResult ?: // Unchanged legacy behavior — existing tests keep working as-is.
            return flowOf(Result.Success(fakeUser))
        userProfileFlow.tryEmit(override)
        return userProfileFlow
    }

    /**
     * Test helper — pushes an additional emission onto the live getUserProfile()
     * flow, simulating a Firestore snapshot listener firing again (e.g. to test
     * retry/repair logic that depends on more than one emission). Only useful
     * once userProfileResult has been set at least once, since that's what
     * switches getUserProfile() from the static flowOf(...) to this shared flow.
     */
    fun emitUserProfile(result: Result<User>) {
        userProfileFlow.tryEmit(result)
    }

    override suspend fun getUserProfileOnce(uid: String): Result<User> =
        Result.Success(fakeUser)

    // ── Write ─────────────────────────────────────────────────────────
    override suspend fun createUserProfile(
        uid: String, displayName: String, email: String, photoUrl: String?,
    ): Result<Unit> = Result.Success(Unit)

    override suspend fun createUserProfileIfNotExists(
        uid: String, displayName: String, email: String, photoUrl: String?,
    ): Result<Unit> {
        createUserProfileIfNotExistsCallCount++
        lastCreateUserProfileIfNotExistsArgs = CreateProfileArgs(uid, displayName, email, photoUrl)
        return createUserProfileIfNotExistsResult
    }

    override suspend fun addXp(uid: String, points: Int): Result<Unit> {
        totalXpAdded += points
        fakeUser = fakeUser.copy(xp = fakeUser.xp + points)
        return Result.Success(Unit)
    }

    override suspend fun awardBadge(uid: String, badge: String): Result<Unit> {
        awardedBadges.add(badge)
        return Result.Success(Unit)
    }

    override suspend fun markQuizCompleted(uid: String, quizId: String): Result<Unit> {
        completedQuizIds.add(quizId)
        return Result.Success(Unit)
    }

    override suspend fun markModuleCompleted(uid: String, moduleId: String): Result<Unit> {
        completedModuleIds.add(moduleId)
        return Result.Success(Unit)
    }

    override suspend fun updateFcmToken(uid: String, token: String): Result<Unit> =
        Result.Success(Unit)

    override suspend fun updateLastSignedIn(uid: String): Result<Unit> =
        Result.Success(Unit)

    override suspend fun saveCertificate(certificate: Certificate): Result<Unit> =
        try {
            savedCertificates.add(certificate)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
}