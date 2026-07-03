package com.example.cybershield.core.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.example.cybershield.core.domain.util.Result

@Singleton
class FirestoreLeaderboardDataSource
@Inject
constructor(
    private val firestore: FirebaseFirestore,
) {
    /**
     * Reads from the `leaderboard` collection — a mirror of only the
     * public-safe fields (displayName, xp, badges), kept in sync by
     * UserRepositoryImpl whenever xp/badges/displayName change.
     *
     * Deliberately NOT the `users` collection: that document also holds
     * email, fcmToken, photoUrl, completedQuizzes, etc., all of which
     * would otherwise stream to every authenticated client via this
     * listener.
     */
    fun topUsers(limit: Int): Flow<Result<List<Map<String, Any?>>>> =
        callbackFlow {
            val registration =
                firestore
                    .collection("leaderboard")
                    .orderBy("xp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .addSnapshotListener { snap, error ->
                        if (error != null) {
                            // Propagate instead of swallowing — silently emitting
                            // emptyList() here previously hid real network/permission
                            // failures behind what looked like a valid "no entries" state.
                            trySend(Result.Error(Exception(error)))
                            return@addSnapshotListener
                        }
                        val list =
                            snap?.documents?.map { doc ->
                                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                                data["_docId"] = doc.id
                                data
                            } ?: emptyList()
                        trySend(Result.Success(list))
                    }
            awaitClose { registration.remove() }
        }
}