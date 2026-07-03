package com.example.cybershield.core.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreLeaderboardDataSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
    ) {
        fun topUsers(limit: Int): Flow<List<Map<String, Any?>>> =
            callbackFlow {
                val registration =
                    firestore
                        .collection("users")
                        .orderBy("xp", Query.Direction.DESCENDING)
                        .limit(limit.toLong())
                        .addSnapshotListener { snap, error ->
                            if (error != null) {
                                trySend(emptyList())
                                return@addSnapshotListener
                            }
                            val list =
                                snap?.documents?.map { doc ->
                                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                                    data["_docId"] = doc.id
                                    data
                                } ?: emptyList()
                            trySend(list)
                        }
                awaitClose { registration.remove() }
            }
    }
