package com.example.cybershield.core.testing.fake

import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.ModuleCompleteResult
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeUserRepository : UserRepository {
    // ── Mutable test state — inspect these in assertions ──────────────
    var fakeUser: User =
        User(
            uid = "test-uid",
            displayName = "Test User",
            email = "test@cybershield.com",
        )
    val completedModuleIds = mutableListOf<String>()

    /** Result returned by [completeModule]; override in tests to simulate failure. */
    var completeModuleResult: (moduleId: String) -> Result<ModuleCompleteResult> = { Result.Success(ModuleCompleteResult(alreadyCompleted = false, xpEarned = 0)) }

    // ── getUserProfile() override controls ─────────────────────────────
    // Default (null) preserves the original behavior exactly: a fixed one-shot
    // flowOf(Result.Success(fakeUser)). Set this to drive Loading/Error states,
    // or to test multi-emission scenarios (e.g. a profile-repair retry path)
    // alongside emitUserProfile().
    var userProfileResult: Result<User>? = null
    private val userProfileFlow = MutableSharedFlow<Result<User>>(replay = 1)

    // ── createUserProfile() call tracking ───────────────────────────────
    var createUserProfileResult: Result<Unit> = Result.Success(Unit)
    var createUserProfileCallCount = 0
    var lastCreateUserProfileArgs: CreateProfileArgs? = null

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

//    // ── Read ──────────────────────────────────────────────────────────
//    override fun getUserProfile(uid: String): Flow<Result<User>> {
//        val override =
//            userProfileResult ?: // Unchanged legacy behavior — existing tests keep working as-is.
//            return flowOf(Result.Success(fakeUser))
//        userProfileFlow.tryEmit(override)
//        return userProfileFlow
//    }

    private val profileFlows = mutableMapOf<String, MutableSharedFlow<Result<User>>>()

    private fun flowFor(uid: String): MutableSharedFlow<Result<User>> =
        profileFlows.getOrPut(uid) { MutableSharedFlow(replay = 1) }

    /**
     * @param emitImmediately when false, the flow is created but nothing is
     * emitted yet — useful for asserting the initial/loading state before
     * a result arrives. Call [emitUserProfile] later to push the value.
     */
    fun setUserProfile(
        uid: String,
        user: User,
        emitImmediately: Boolean = true,
    ) {
        if (emitImmediately) {
            flowFor(uid).tryEmit(Result.Success(user))
        } else {
            // ensure the flow exists so collectors don't get anything to collect from
            flowFor(uid)
        }
    }

    fun setUserProfileError(
        uid: String,
        exception: Exception,
    ) {
        flowFor(uid).tryEmit(Result.Error(exception))
    }

    /** Push a new emission onto an already-created flow for [uid]. */
    fun emitUserProfile(
        uid: String,
        user: User,
    ) {
        flowFor(uid).tryEmit(Result.Success(user))
    }

    override fun getUserProfile(uid: String): Flow<Result<User>> {
        val flow = flowFor(uid)
        // Only seed a default emission if this uid's flow is still empty —
        // i.e. the test never called setUserProfile()/setUserProfileError()
        // for it. Otherwise this would clobber whatever the test set up
        // (replay = 1 means the newest tryEmit always wins).
        if (flow.replayCache.isEmpty()) {
            val result = userProfileResult ?: Result.Success(fakeUser)
            flow.tryEmit(result)
        }
        return flow
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

    override suspend fun getUserProfileOnce(uid: String): Result<User> = Result.Success(fakeUser)

    // ── Write ─────────────────────────────────────────────────────────
    override suspend fun createUserProfile(
        uid: String,
        displayName: String,
        email: String,
        photoUrl: String?,
    ): Result<Unit> {
        createUserProfileCallCount++
        lastCreateUserProfileArgs = CreateProfileArgs(uid, displayName, email, photoUrl)
        return createUserProfileResult
    }

    override suspend fun createUserProfileIfNotExists(
        uid: String,
        displayName: String,
        email: String,
        photoUrl: String?,
    ): Result<Unit> {
        createUserProfileIfNotExistsCallCount++
        lastCreateUserProfileIfNotExistsArgs = CreateProfileArgs(uid, displayName, email, photoUrl)
        return createUserProfileIfNotExistsResult
    }

    override suspend fun completeModule(
        uid: String,
        moduleId: String,
    ): Result<ModuleCompleteResult> {
        val result = completeModuleResult(moduleId)
        if (result is Result.Success && !result.data.alreadyCompleted) {
            completedModuleIds.add(moduleId)
            fakeUser = fakeUser.copy(xp = fakeUser.xp + result.data.xpEarned)
        }
        return result
    }

    override suspend fun updateFcmToken(
        uid: String,
        token: String,
    ): Result<Unit> = Result.Success(Unit)

    override suspend fun updateLastSignedIn(uid: String): Result<Unit> = Result.Success(Unit)
}