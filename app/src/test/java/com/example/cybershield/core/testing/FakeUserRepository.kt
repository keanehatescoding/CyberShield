package com.example.cybershield.core.testing

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
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

    // ── Read ──────────────────────────────────────────────────────────
    override fun getUserProfile(uid: String): Flow<Result<User>> =
        flowOf(Result.Success(fakeUser))

    override suspend fun getUserProfileOnce(uid: String): Result<User> =
        Result.Success(fakeUser)

    // ── Write ─────────────────────────────────────────────────────────
    override suspend fun createUserProfile(
        uid: String, displayName: String, email: String, photoUrl: String?,
    ): Result<Unit> = Result.Success(Unit)

    override suspend fun createUserProfileIfNotExists(
        uid: String, displayName: String, email: String, photoUrl: String?,
    ): Result<Unit> = Result.Success(Unit)

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
        try{
            savedCertificates.add(certificate)
            Result.Success(Unit)
        }catch (e: Exception){
            Result.Error(e)
        }
}