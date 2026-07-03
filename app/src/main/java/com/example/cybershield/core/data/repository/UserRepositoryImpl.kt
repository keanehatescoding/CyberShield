package com.example.cybershield.core.data.repository

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.resultOf
import com.example.cybershield.core.firebase.FirestoreUserDataSource
import com.example.cybershield.core.firebase.model.UserDto
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl
@Inject
constructor(
    private val remoteSource: FirestoreUserDataSource,
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
                    "xp" to 0,
                    "level" to 1,
                    "badges" to emptyList<String>(),
                    "completedQuizzes" to emptyList<String>(),
                    "completedModules" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            remoteSource.userDoc(uid).set(profile).await()

            // Mirror only the public-safe fields into `leaderboard/{uid}` —
            // never email/photoUrl/fcmToken. See leaderboardDoc() kdoc.
            val leaderboardProfile =
                mapOf(
                    "displayName" to displayName,
                    "xp" to 0,
                    "badges" to emptyList<String>(),
                )
            remoteSource.leaderboardDoc(uid).set(leaderboardProfile).await()

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

            // Mirror displayName only — mirrors the "users" doc behavior above:
            // xp/badges are deliberately NOT in this map, so a merge on a returning
            // user's leaderboard doc leaves their existing xp/badges untouched. If
            // the doc doesn't exist yet, xp/badges will simply be absent until the
            // first addXp()/awardBadge() call; getTopLeaderboard() already falls
            // back to 0/empty for missing fields.
            val leaderboardProfile = mapOf("displayName" to displayName)
            remoteSource.leaderboardDoc(uid).set(leaderboardProfile, SetOptions.merge()).await()

            Result.Success(Unit)
        }

    // ── Add XP atomically ──────────────────────────────────────────────
    override suspend fun addXp(
        uid: String,
        points: Int,
    ): Result<Unit> =
        resultOf {
            remoteSource
                .userDoc(uid)
                .update(
                    "xp",
                    FieldValue.increment(points.toLong()),
                ).await()
            // Same increment, same value, on the public mirror — keeps
            // leaderboard/{uid}.xp consistent with users/{uid}.xp.
            remoteSource
                .leaderboardDoc(uid)
                .set(
                    mapOf("xp" to FieldValue.increment(points.toLong())),
                    SetOptions.merge(),
                ).await()
            Result.Success(Unit)
        }

    // ── Award badge (idempotent) ───────────────────────────────────────
    override suspend fun awardBadge(
        uid: String,
        badge: String,
    ): Result<Unit> =
        resultOf {
            remoteSource
                .userDoc(uid)
                .update(
                    "badges",
                    FieldValue.arrayUnion(badge),
                ).await()
            // Mirror onto the public leaderboard doc — LeaderboardScreen shows
            // a badge count per entry, so this needs to stay in sync too.
            remoteSource
                .leaderboardDoc(uid)
                .set(
                    mapOf("badges" to FieldValue.arrayUnion(badge)),
                    SetOptions.merge(),
                ).await()
            Result.Success(Unit)
        }

    // ── Mark quiz completed ────────────────────────────────────────────
    override suspend fun markQuizCompleted(
        uid: String,
        quizId: String,
    ): Result<Unit> =
        resultOf {
            remoteSource
                .userDoc(uid)
                .update(
                    "completedQuizzes",
                    FieldValue.arrayUnion(quizId),
                ).await()
            Result.Success(Unit)
        }

    // ── Mark module completed ──────────────────────────────────────────
    override suspend fun markModuleCompleted(
        uid: String,
        moduleId: String,
    ): Result<Unit> =
        resultOf {
            remoteSource
                .userDoc(uid)
                .update(
                    "completedModules",
                    FieldValue.arrayUnion(moduleId),
                ).await()
            Result.Success(Unit)
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

    override suspend fun saveCertificate(certificate: Certificate): Result<Unit> =
        resultOf {
            val data =
                mapOf(
                    "id" to certificate.id,
                    "userId" to certificate.userId,
                    "userName" to certificate.userName,
                    "moduleId" to certificate.moduleId,
                    "moduleName" to certificate.moduleName,
                    "quizTitle" to certificate.quizTitle,
                    "score" to certificate.score,
                    "issuedAt" to certificate.issuedAt,
                )
            remoteSource
                .userDoc(certificate.userId)
                .collection("certificates")
                .document(certificate.id)
                .set(data)
                .await()
            Result.Success(Unit)
        }
}