package com.example.cybershield

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner required by Hilt for instrumented tests.
 * Swaps in HiltTestApplication so @HiltAndroidTest-annotated tests
 * get a Hilt component graph built specifically for testing, with
 * support for @BindValue and test-specific module overrides.
 *
 * Referenced in app/build.gradle.kts via:
 *   testInstrumentationRunner = "com.example.cybershield.HiltTestRunner"
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
