package com.example.cybershield.core.data.di

import com.example.cybershield.core.data.repository.CertificateRepositoryImpl
import com.example.cybershield.core.data.repository.LeaderboardRepositoryImpl
import com.example.cybershield.core.data.repository.ModuleRepositoryImpl
import com.example.cybershield.core.data.repository.QuizRepositoryImpl
import com.example.cybershield.core.data.repository.UserRepositoryImpl
import com.example.cybershield.core.domain.repository.CertificateRepository
import com.example.cybershield.core.domain.repository.LeaderboardRepository
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

    @Binds
    @Singleton
    abstract fun bindCertificateRepository(impl: CertificateRepositoryImpl): CertificateRepository

    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(impl: LeaderboardRepositoryImpl): LeaderboardRepository
}
