package com.example.cybershield.core.data.repository

import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.ModuleCompleteResult
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.resultOf
import com.example.cybershield.core.firebase.FirestoreUserDataSource
import com.example.cybershield.core.firebase.FunctionsModuleDataSource
import com.example.cybershield.core.firebase.model.UserDto
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl
    @Inject
    constructor(
        private val remoteSource: FirestoreUserDataSource,
        private val functionsModuleDataSource: FunctionsModuleDataSource,
    ) : UserRepository {
        override fun getUserProfile(uid: String): Flow<Result<User>> =
            callbackFlow {
                trySend(Result.Loading)

                val listener =
                    remoteSource
                        .userDoc(uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                trySend(Result.Error(Exception(error)))
                                return@addSnapshotListener
                            }
                            val user = snapshot?.toObject<UserDto>()?.toDomain()
                            if (user != null) {
                                trySend(Result.Success(user))
                            } else {
                                trySend(Result.Error(Exception("User not found")))
                            }
                        }
                awaitClose { listener.remove() }
            }

        // ── One-shot fetch ─────────────────────────────────────────────────
        override suspend fun getUserProfileOnce(uid: String): Result<User> =
            resultOf {
                val snapshot = remoteSource.userDoc(uid).get().await()
                val user = snapshot.toObject<UserDto>()?.toDomain()
                if (user != null) {
                    Result.Success(user)
                } else {
                    Result.Error(Exception("User not found"))
                }
            }

        // ── Create profile (first registration) ───────────────────────────
        override suspend fun createUserProfile(
            uid: String,
            displayName: String,
            email: String,
            photoUrl: String?,
        ): Result<Unit> =
            resultOf {
                val profile =
                    mapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "photoUrl" to photoUrl,
                        "lastSignedInAt" to FieldValue.serverTimestamp(),
                        "level" to 1,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                remoteSource.userDoc(uid).set(profile).await()

                // leaderboard/{uid} is no longer written from the client — the
                // onUserProfileWritten Cloud Function trigger mirrors
                // displayName/xp:0/badges:[] the moment these users/{uid} doc is
                // created (and displayName on later edits). See firestore.rules
                // for why: leaderboard is client-read-only now.

                Result.Success(Unit)
            }

        // ── Create profile only if it doesn't exist (Google SSO) ──────────
        override suspend fun createUserProfileIfNotExists(
            uid: String,
            displayName: String,
            email: String,
            photoUrl: String?,
        ): Result<Unit> =
            resultOf {
                val profile =
                    mapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "photoUrl" to photoUrl,
                        // in case a user already exists and is trying to sign in via Google then there is
                        // no need of overriding their progress
                    )
                // merge = true → creates if missing, updates lastSignedInAt
                // if exists — never overwrites xp, badges, completedQuizzes
                remoteSource.userDoc(uid).set(profile, SetOptions.merge()).await()

                // leaderboard/{uid} is no longer written from the client — the
                // onUserProfileWritten trigger mirrors displayName there on both
                // create (new user, xp/badges seeded at 0/[]) and update (e.g. a
                // returning Google SSO user whose Google display name changed),
                // leaving xp/badges untouched on updates.

                Result.Success(Unit)
            }

        // ── Complete a module + award its xpReward (server-side) ───────────
        override suspend fun completeModule(
            uid: String,
            moduleId: String,
            watchedMs: Long,
        ): Result<ModuleCompleteResult> =
            withContext(Dispatchers.IO) {
                functionsModuleDataSource.completeModule(moduleId, watchedMs)
            }

        // ── Save FCM token ─────────────────────────────────────────────────
        override suspend fun updateFcmToken(
            uid: String,
            token: String,
        ): Result<Unit> =
            resultOf {
                remoteSource.userDoc(uid).update("fcmToken", token).await()
                Result.Success(Unit)
            }

        // ── Update last sign-in ────────────────────────────────────────────
        override suspend fun updateLastSignedIn(uid: String): Result<Unit> =
            resultOf {
                remoteSource
                    .userDoc(uid)
                    .update(
                        "lastSignedInAt",
                        FieldValue.serverTimestamp(),
                    ).await()
                Result.Success(Unit)
            }
    }
