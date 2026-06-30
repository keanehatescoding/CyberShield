package com.example.cybershield.core.testing.di

import com.example.cybershield.core.data.di.UseCaseModule
import com.example.cybershield.core.domain.usecase.EnsureUserProfileUseCase
import com.example.cybershield.core.domain.usecase.FakeEnsureUserProfileUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.testing.TestInstallIn

@Module
@TestInstallIn(
    components = [ViewModelComponent::class],
    replaces = [UseCaseModule::class],
)
abstract class FakeUseCaseModule {
    @Binds
    @ViewModelScoped
    abstract fun bindEnsureUserProfileUseCase(
        impl: FakeEnsureUserProfileUseCase,
    ): EnsureUserProfileUseCase
}