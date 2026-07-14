package com.example.cybershield.core.domain.util

/**
 * Abstraction over crash/error telemetry (backed by Crashlytics in
 * [com.example.cybershield.core.firebase.FirebaseCrashReporter]).
 *
 * Use-case and worker layers depend on this instead of Firebase directly, so
 * a caught-and-otherwise-swallowed [Throwable] still produces a signal
 * instead of silently disappearing, without coupling domain code to a
 * specific vendor SDK.
 */
interface CrashReporter {
    /**
     * Records a non-fatal exception that was caught and handled (the app
     * keeps running), so it still shows up in Crashlytics instead of
     * vanishing silently.
     *
     * @param keys optional key/value context (e.g. a resultId) attached to
     *   the report to help diagnose it later. Never put PII in here.
     */
    fun recordException(
        throwable: Throwable,
        keys: Map<String, String> = emptyMap(),
    )
}
