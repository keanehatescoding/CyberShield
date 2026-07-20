package com.example.cybershield.core.data.repository

import com.example.cybershield.core.domain.model.LeaderboardEntry
import com.example.cybershield.core.domain.repository.LeaderboardRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.map
import com.example.cybershield.core.firebase.FirestoreLeaderboardDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaderboardRepositoryImpl
    @Inject
    constructor(
        private val leaderboardDataSource: FirestoreLeaderboardDataSource,
    ) : LeaderboardRepository {
        override fun getTopLeaderboard(limit: Int): Flow<Result<List<LeaderboardEntry>>> =
            leaderboardDataSource
                .topUsers(limit)
                .map { result ->
                    result.map { docs ->
                        docs.mapNotNull { data ->
                            val uid = data["_docId"] as? String ?: return@mapNotNull null
                            // `as? Number` (not `as? Long`) — Firestore's Map<String, Any?>
                            // representation isn't guaranteed to hand back a Long for every
                            // numeric field, and a bare `as? Long` silently returns null (and
                            // therefore the `?: 0` fallback) for anything else, which is why
                            // every entry rendered as xp=0.
                            val xp = (data["xp"] as? Number)?.toInt() ?: 0
                            LeaderboardEntry(
                                uid = uid,
                                displayName = data["displayName"] as? String ?: "Anonymous",
                                xp = xp,
                                // Derived from xp, not the stored `level` field: `level` on the
                                // user doc is only ever written once at profile creation and is
                                // never incremented, so it would still read 1 forever even with
                                // the cast fixed. Every other screen (Profile, Home) already
                                // shows `User.computedLevel` instead of the raw field — this
                                // matches that behavior. Keep the "100 xp per level" constant in
                                // sync with User.computedLevel if that ever changes.
                                level = (xp / 100) + 1,
                                badges = (data["badges"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            )
                        }
                    }
                }
    }
