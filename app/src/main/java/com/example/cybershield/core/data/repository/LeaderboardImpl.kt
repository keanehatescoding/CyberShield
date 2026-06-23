package com.example.cybershield.core.data.repository

import com.example.cybershield.core.domain.repository.LeaderboardRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.feature.leaderboard.LeaderboardEntry
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class LeaderboardRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : LeaderboardRepository {

    // ★ Exact same callbackFlow + awaitClose pattern from the listener-leak
    // fix earlier — unchanged logic, just relocated to where it belongs
    override fun getTopLeaderboard(limit: Int): Flow<Result<List<LeaderboardEntry>>> = callbackFlow {
        val registration = firestore
            .collection("users")
            .orderBy("xp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(Result.Error(error))
                    return@addSnapshotListener
                }
                val entries = snap?.documents?.mapNotNull { doc ->
                    LeaderboardEntry(
                        uid = doc.id,
                        displayName = doc.getString("displayName") ?: "Anonymous",
                        xp = doc.getLong("xp")?.toInt() ?: 0,
                        level = doc.getLong("level")?.toInt() ?: 1,
                        badges = (doc.get("badges") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList(),
                    )
                } ?: emptyList()
                trySend(Result.Success(entries))
            }
        awaitClose { registration.remove() }
    }
}