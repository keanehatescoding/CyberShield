package com.example.cybershield.core.testing.di

import com.example.cybershield.core.data.di.NetworkModule
import com.example.cybershield.core.firebase.di.DataSourceModule
import com.example.cybershield.core.firebase.di.FirebaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Replaces [FirebaseModule], [DataSourceModule], and [NetworkModule] for all
 * @HiltAndroidTest instrumented tests.
 *
 * FirebaseModule provides FirebaseAuth/Firestore/Storage/Messaging — all of which
 * require a real google-services.json and network. DataSourceModule depends on
 * Firestore. NetworkModule's OkHttpClient depends on AuthInterceptor which depends
 * on FirebaseAuth. Replacing all three here breaks the entire Firebase dependency
 * chain so no real I/O occurs in graph tests.
 *
 * We don't provide FirestoreQuizDataSource or FirestoreUserDataSource because
 * FakeRepositoryModule already replaces the repositories that would normally consume
 * them — Hilt never needs to build those data sources at all.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [FirebaseModule::class, DataSourceModule::class, NetworkModule::class],
)
object FakeFirebaseModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
}