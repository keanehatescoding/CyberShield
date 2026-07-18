package com.example.cybershield.core.firebase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test

/**
 * Crashlytics custom keys are process-global: [FirebaseCrashlytics.setCustomKey]
 * persists a value across every later report until something overwrites it.
 * These tests guard against a key set for one exception (e.g. "quizId")
 * silently showing up attached to a later, unrelated exception that never
 * set it.
 */
class FirebaseCrashReporterTest {
    private lateinit var crashlytics: FirebaseCrashlytics
    private lateinit var reporter: FirebaseCrashReporter

    @Before
    fun setUp() {
        crashlytics = mockk(relaxed = true)
        reporter = FirebaseCrashReporter(crashlytics)
    }

    @Test
    fun `recordException blanks every known key before applying the ones it was given`() {
        every { crashlytics.setCustomKey(any<String>(), any<String>()) } returns Unit

        reporter.recordException(RuntimeException("boom"), mapOf("quizId" to "quiz-1"))

        verifyOrder {
            crashlytics.setCustomKey("quizId", "")
            crashlytics.setCustomKey("resultId", "")
            crashlytics.setCustomKey("quizId", "quiz-1")
            crashlytics.recordException(any())
        }
    }

    @Test
    fun `a report with no keys still blanks previously-set keys instead of inheriting them`() {
        reporter.recordException(RuntimeException("first"), mapOf("resultId" to "r1"))
        reporter.recordException(RuntimeException("second"))

        verifyOrder {
            crashlytics.setCustomKey("resultId", "r1")
            crashlytics.recordException(any())
            // Second call must blank "resultId" again — otherwise "r1" would
            // still be attached to this unrelated second report.
            crashlytics.setCustomKey("resultId", "")
            crashlytics.recordException(any())
        }
    }
}
