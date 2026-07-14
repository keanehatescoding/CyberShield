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
            keys.forEach { (key, value) -> crashlytics.setCustomKey(key, value) }
            crashlytics.recordException(throwable)
        }
    }
