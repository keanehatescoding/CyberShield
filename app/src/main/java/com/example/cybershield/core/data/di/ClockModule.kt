package com.example.cybershield.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * Provides the real system Clock for production use, in the device's default
 * timezone — matching what java.util.Calendar.getInstance() used to give
 * HomeViewModel.greeting() implicitly. Singleton-scoped since a Clock carries
 * no per-screen state; tests inject a fixed Clock directly into the ViewModel
 * constructor instead of going through this module.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}