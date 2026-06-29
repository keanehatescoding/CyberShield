package com.example.cybershield.core.data.di

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provides a monotonic elapsed-time source for production use, backed by
 * SystemClock.elapsedRealtime() — used for measuring durations (e.g. quiz
 * timeTaken) where wall-clock jumps (timezone changes, NTP corrections,
 * user adjusting the system clock) must not affect the result. Unscoped
 * since each call returns a fresh reading rather than a cached value; tests
 * inject a fake provider directly into the ViewModel constructor instead of
 * going through this module.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideElapsedRealtimeProvider(): () -> Long = { SystemClock.elapsedRealtime() }
}