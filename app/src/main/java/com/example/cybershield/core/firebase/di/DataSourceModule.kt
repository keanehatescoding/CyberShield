package com.example.cybershield.core.firebase.di

import com.example.cybershield.core.firebase.FirestoreQuizDataSource
import com.example.cybershield.core.firebase.FirestoreUserDataSource
import com.example.cybershield.core.firebase.FunctionsQuizDataSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {
    @Provides
    @Singleton
    fun provideFirestoreUserDataSource(firestore: FirebaseFirestore): FirestoreUserDataSource = FirestoreUserDataSource(firestore)

    @Provides
    @Singleton
    fun provideFirestoreQuizDataSource(firestore: FirebaseFirestore): FirestoreQuizDataSource = FirestoreQuizDataSource(firestore)

    @Provides
    @Singleton
    fun provideFunctionsQuizDataSource(functions: FirebaseFunctions): FunctionsQuizDataSource = FunctionsQuizDataSource(functions)
}
