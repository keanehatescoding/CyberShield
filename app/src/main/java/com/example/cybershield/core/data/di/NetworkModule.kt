package com.example.cybershield.core.data.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Reserved for future custom backend API client.
 *
 * If CyberShield ever needs to call a REST backend (e.g. Cloud Functions),
 * uncomment the OkHttpClient provider below and wire up AuthInterceptor.
 *
 *   @Provides @Singleton
 *   fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
 *       OkHttpClient.Builder().addInterceptor(authInterceptor).build()
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule
