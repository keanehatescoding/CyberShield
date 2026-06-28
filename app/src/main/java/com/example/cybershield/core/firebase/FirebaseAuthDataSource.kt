package com.example.cybershield.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around FirebaseAuth. Throws raw Firebase exceptions —
 * translation into [AuthError] happens one layer up, in AuthRepositoryImpl.
 * This class has no opinions about error messages or app-level state.
 */
@Singleton
class FirebaseAuthDataSource @Inject constructor(
    private val auth: FirebaseAuth,
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun authStateChanges(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun createUserWithEmailAndPassword(email: String, password: String): FirebaseUser? {
        return auth.createUserWithEmailAndPassword(email, password).await().user
    }

    suspend fun updateDisplayName(user: FirebaseUser, name: String) {
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name).build()
        ).await()
    }

    suspend fun sendEmailVerification(user: FirebaseUser) {
        user.sendEmailVerification().await()
    }

    suspend fun signInWithEmailAndPassword(email: String, password: String): FirebaseUser? {
        return auth.signInWithEmailAndPassword(email, password).await().user
    }

    /** Reloads [currentUser] in place and returns it post-reload. */
    suspend fun reloadCurrentUser(): FirebaseUser? {
        val user = auth.currentUser ?: return null
        user.reload().await()
        return user
    }

    fun signOut() = auth.signOut()
}