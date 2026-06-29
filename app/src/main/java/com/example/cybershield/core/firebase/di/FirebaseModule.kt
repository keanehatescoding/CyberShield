package com.example.cybershield.core.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // lives for the entire app lifetime
object FirebaseModule {
    // ── Firebase Auth ──────────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    // ── Firestore ──────────────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()

        // Enable offline persistence with a 100MB local cache
        // This is the Firestore-level offline cache (separate from Room)
        firestore.firestoreSettings =
            firestoreSettings {
                setLocalCacheSettings(
                    persistentCacheSettings {
                        setSizeBytes(100 * 1024 * 1024L) // 100 MB
                    },
                )
            }
        return firestore
    }

    // ── Firebase Storage ───────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    // ── Firebase Messaging ─────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
