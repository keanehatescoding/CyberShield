package com.example.cybershield.core.data.repository

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.firebase.model.UserDto
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserRepository {

    // Convenience reference to the users collection
    private fun userDoc(uid: String) =
        firestore.collection("users").document(uid)

    // ── Real-time profile stream ───────────────────────────────────────
    override fun getUserProfile(uid: String): Flow<Result<User>> = callbackFlow {
        trySend(Result.Loading)

        val listener = userDoc(uid)
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
        try {
            val snapshot = userDoc(uid).get().await()
            val user     = snapshot.toObject<UserDto>()?.toDomain()
            if (user != null) Result.Success(user)
            else Result.Error(Exception("User not found"))
        } catch (e: Exception) {
            Result.Error(e)
        }

    // ── Create profile (first registration) ───────────────────────────
    override suspend fun createUserProfile(
        uid:         String,
        displayName: String,
        email:       String,
        photoUrl:    String?,
    ): Result<Unit> = try {
        val profile = mapOf(
            "uid"             to uid,
            "displayName"     to displayName,
            "email"           to email,
            "photoUrl"        to photoUrl,
            "xp"              to 0,
            "level"           to 1,
            "badges"          to emptyList<String>(),
            "completedQuizzes" to emptyList<String>(),
            "completedModules" to emptyList<String>(),
            "createdAt"       to FieldValue.serverTimestamp(),
            "lastSignedInAt"  to FieldValue.serverTimestamp(),
        )
        userDoc(uid).set(profile).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    // ── Create profile only if it doesn't exist (Google SSO) ──────────
    override suspend fun createUserProfileIfNotExists(
        uid:         String,
        displayName: String,
        email:       String,
        photoUrl:    String?,
    ): Result<Unit> = try {
        val profile = mapOf(
            "uid"             to uid,
            "displayName"     to displayName,
            "email"           to email,
            "photoUrl"        to photoUrl,
            "xp"              to 0,
            "level"           to 1,
            "badges"          to emptyList<String>(),
            "completedQuizzes" to emptyList<String>(),
            "completedModules" to emptyList<String>(),
            "lastSignedInAt"  to FieldValue.serverTimestamp(),
        )
        // merge = true → creates if missing, updates lastSignedInAt
        // if exists — never overwrites xp, badges, completedQuizzes
        userDoc(uid).set(profile, SetOptions.merge()).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    // ── Add XP atomically ──────────────────────────────────────────────
    override suspend fun addXp(uid: String, points: Int): Result<Unit> =
        try {
            userDoc(uid).update(
                "xp", FieldValue.increment(points.toLong())
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }

    // ── Award badge (idempotent) ───────────────────────────────────────
    override suspend fun awardBadge(uid: String, badge: String): Result<Unit> =
        try {
            userDoc(uid).update(
                "badges", FieldValue.arrayUnion(badge)
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }

    // ── Mark quiz completed ────────────────────────────────────────────
    override suspend fun markQuizCompleted(uid: String, quizId: String): Result<Unit> =
        try {
            userDoc(uid).update(
                "completedQuizzes", FieldValue.arrayUnion(quizId)
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }

    // ── Mark module completed ──────────────────────────────────────────
    override suspend fun markModuleCompleted(uid: String, moduleId: String): Result<Unit> =
        try {
            userDoc(uid).update(
                "completedModules", FieldValue.arrayUnion(moduleId)
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }

    // ── Save FCM token ─────────────────────────────────────────────────
    override suspend fun updateFcmToken(uid: String, token: String): Result<Unit> =
        try {
            userDoc(uid).update("fcmToken", token).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }

    // ── Update last sign-in ────────────────────────────────────────────
    override suspend fun updateLastSignedIn(uid: String): Result<Unit> =
        try {
            userDoc(uid).update(
                "lastSignedInAt", FieldValue.serverTimestamp()
            ).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    override suspend fun saveCertificate(certificate: Certificate) {
        try {
            val data = mapOf(
                "id"         to certificate.id,
                "userId"     to certificate.userId,
                "userName"   to certificate.userName,
                "moduleId"   to certificate.moduleId,
                "moduleName" to certificate.moduleName,
                "quizTitle"  to certificate.quizTitle,
                "score"      to certificate.score,
                "issuedAt"   to certificate.issuedAt,
            )
            userDoc(certificate.userId)
                .collection("certificates")
                .document(certificate.id)
                .set(data)
                .await()
        } catch (_: Exception) {
            // non-fatal — cert still exists in memory for the session
        }
    }
}