package com.example.cybershield.core.firebase

import com.example.cybershield.core.firebase.model.UserDto
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreUserDataSource
@Inject
constructor(
    private val firestore: FirebaseFirestore,
) {
    fun userDoc(uid: String) = firestore.collection("users").document(uid)

    /**
     * Public-safe mirror of a user's leaderboard-relevant fields
     * (displayName, xp, badges). Only ever contains fields that are
     * safe to expose to every authenticated client — never email,
     * fcmToken, photoUrl, completedQuizzes, etc. Kept in sync from
     * UserRepositoryImpl; see FirestoreLeaderboardDataSource for reads.
     */
    fun leaderboardDoc(uid: String) = firestore.collection("leaderboard").document(uid)

    suspend fun getUser(uid: String): UserDto? = userDoc(uid).get().await().toObject<UserDto>()

    suspend fun userExists(uid: String): Boolean = userDoc(uid).get().await().exists()
}