package com.example.cybershield.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreAuthDataSource @Inject constructor(
    private val auth: FirebaseAuth,
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Emits the current FirebaseUser whenever auth state changes.
     * Emits null when the user signs out.
     */
    fun authStateChanges(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
}