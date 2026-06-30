package com.example.cybershield.core.testing.di

import com.example.cybershield.core.data.di.AuthModule
import com.example.cybershield.core.data.di.RepositoryModule
import com.example.cybershield.core.domain.repository.AuthRepository
import com.example.cybershield.core.domain.repository.CertificateRepository
import com.example.cybershield.core.domain.repository.LeaderboardRepository
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.testing.fake.FakeAuthRepository
import com.example.cybershield.core.testing.fake.FakeCertificateRepository
import com.example.cybershield.core.testing.fake.FakeLeaderboardRepository
import com.example.cybershield.core.testing.fake.FakeModuleRepository
import com.example.cybershield.core.testing.fake.FakeQuizRepository
import com.example.cybershield.core.testing.fake.FakeUserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [com.example.cybershield.core.data.di.RepositoryModule] and [com.example.cybershield.core.data.di.AuthModule] for all @HiltAndroidTest instrumented tests.
 * Every real repository binding is swapped for its in-memory fake so no Firebase I/O occurs.
 *
 * If an individual test needs to control fake state (e.g. seed leaderboard entries), it can
 * inject the concrete fake type via @Inject and call its test helpers directly:
 *
 *     @Inject lateinit var fakeQuizRepository: FakeQuizRepository
 *
 * Note: the Fake* classes must be @Singleton-scoped here to match production scope,
 * otherwise Hilt creates a new instance for each injection site and test-state mutations
 * on one reference won't be visible through another.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class, AuthModule::class],
)
abstract class FakeRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FakeAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindQuizRepository(impl: FakeQuizRepository): QuizRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: FakeUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindModuleRepository(impl: FakeModuleRepository): ModuleRepository

    @Binds
    @Singleton
    abstract fun bindCertificateRepository(impl: FakeCertificateRepository): CertificateRepository

    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(impl: FakeLeaderboardRepository): LeaderboardRepository
}