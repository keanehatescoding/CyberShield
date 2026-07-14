package com.example.cybershield.core.firebase.di

import com.example.cybershield.core.domain.util.CrashReporter
import com.example.cybershield.core.firebase.FirebaseCrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CrashReportingModule {
    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: FirebaseCrashReporter): CrashReporter
}
