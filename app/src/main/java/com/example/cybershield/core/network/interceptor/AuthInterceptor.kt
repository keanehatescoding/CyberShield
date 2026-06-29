package com.example.cybershield.core.network.interceptor

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * OkHttp interceptor that appends a Firebase ID token as a Bearer token
 * to every outgoing request. Used if CyberShield ever calls a custom
 * backend (e.g. a Cloud Functions REST endpoint).
 *
 * Wire this into OkHttpClient in NetworkModule if needed:
 *   OkHttpClient.Builder().addInterceptor(AuthInterceptor(...)).build()
 */
class AuthInterceptor
    @Inject
    constructor(
        private val auth: FirebaseAuth,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token =
                try {
                    auth.currentUser
                        ?.getIdToken(false) // false = use cached token unless expired
                        ?.let { task ->
                            // ★ Tasks.await() with an explicit timeout — blocks this thread
                            // directly, no coroutine dispatcher spin-up, and bounded so a
                            // hung token refresh can't tie up the dispatcher thread forever
                            Tasks.await(task, 10, TimeUnit.SECONDS)
                        }?.token
                } catch (_: Exception) {
                    // Token refresh failed or timed out — proceed unauthenticated
                    // rather than throwing and breaking the whole request pipeline.
                    // The backend will reject with 401 if auth was actually required.
                    null
                }

            val request =
                if (token != null) {
                    chain
                        .request()
                        .newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }

            return chain.proceed(request)
        }
    }
