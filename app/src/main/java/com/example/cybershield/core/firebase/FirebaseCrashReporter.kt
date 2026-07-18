package com.example.cybershield.core.firebase

import com.example.cybershield.core.domain.util.CrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject

/** [CrashReporter] backed by Firebase Crashlytics. */
class FirebaseCrashReporter
    @Inject
    constructor(
        private val crashlytics: FirebaseCrashlytics,
    ) : CrashReporter {
        override fun recordException(
            throwable: Throwable,
            keys: Map<String, String>,
        ) {
            // Crashlytics custom keys are process-global, not per-report: once
            // set, a key keeps its value on every report that follows until
            // something overwrites it. Without resetting the full known set
            // first, a key attached to one exception (e.g. "quizId") can end
            // up attributed to a later, unrelated exception that never set it.
            // Blanking every known key before applying this call's keys keeps
            // each report scoped to only what it actually provided.
            KNOWN_KEYS.forEach { key -> crashlytics.setCustomKey(key, "") }
            keys.forEach { (key, value) -> crashlytics.setCustomKey(key, value) }
            crashlytics.recordException(throwable)
        }

        companion object {
            /**
             * Every custom key name used anywhere via [CrashReporter.recordException].
             * Add new key names here when a new call site introduces one, so they
             * get cleared too instead of silently leaking into future reports.
             */
            private val KNOWN_KEYS = setOf("quizId", "resultId")
        }
    }
