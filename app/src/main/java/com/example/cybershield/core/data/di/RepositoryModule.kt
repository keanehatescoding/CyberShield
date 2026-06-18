package com.example.cybershield.core.di

import com.example.cybershield.core.data.repository.ModuleRepositoryImpl
import com.example.cybershield.core.data.repository.QuizRepositoryImpl
import com.example.cybershield.core.data.repository.UserRepositoryImpl
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindQuizRepository(impl: QuizRepositoryImpl): QuizRepository

    @Binds
    @Singleton
    abstract fun bindModuleRepository(impl: ModuleRepositoryImpl): ModuleRepository
}