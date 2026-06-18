package com.example.cybershield.core.network.interceptor

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that appends a Firebase ID token as a Bearer token
 * to every outgoing request. Used if CyberShield ever calls a custom
 * backend (e.g. a Cloud Functions REST endpoint).
 *
 * Wire this into OkHttpClient in NetworkModule if needed:
 *   OkHttpClient.Builder().addInterceptor(AuthInterceptor(...)).build()
 */
class AuthInterceptor @Inject constructor(
    private val auth: FirebaseAuth,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            auth.currentUser
                ?.getIdToken(false)   // false = use cached token unless expired
                ?.await()
                ?.token
        }

        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        return chain.proceed(request)
    }
}