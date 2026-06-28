package com.example.cybershield.core.data.di

import com.example.cybershield.core.domain.usecase.EnsureUserProfileUseCase
import com.example.cybershield.core.domain.usecase.EnsureUserProfileUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * Binds EnsureUserProfileUseCase to its implementation, scoped to the ViewModel's
 * lifecycle so the "already attempted a repair" flag resets naturally when the
 * owning ViewModel (and its scope) is cleared — matching the lifetime it had when
 * profileRepairAttempted was a private field directly on HomeViewModel.
 *
 * If you already have a UseCaseModule installed in ViewModelComponent, move this
 * @Binds method into it instead of keeping a separate file.
 */
@Module
@InstallIn(ViewModelComponent::class)
abstract class UseCaseModule {

    @Binds
    @ViewModelScoped
    abstract fun bindEnsureUserProfileUseCase(
        impl: EnsureUserProfileUseCaseImpl,
    ): EnsureUserProfileUseCase
}