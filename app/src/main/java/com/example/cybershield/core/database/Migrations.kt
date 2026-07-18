package com.example.cybershield.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v8 -> v9: adds `finalizeFailureCount` and `abandoned` to `quiz_attempts`.
 *
 * Backs the bounded-retry fix in FinalizeQuizAttemptsUseCase: an attempt can
 * fail to finalize permanently — e.g. the user retook the same quiz before
 * this attempt's answers synced, so its `quizResults` docs on the server got
 * overwritten by the retake and `finalizeQuizAttempt` can never again see a
 * complete set of answers for it. Previously such an attempt just sat at
 * `provisional = true` forever, silently retried by every periodic sync pass
 * with no way to ever succeed and no signal to the user or to Crashlytics.
 * After MAX_FINALIZE_FAILURES failed tries it's now marked `abandoned` and
 * `provisional = false`, so it stops being retried and the failure is
 * reported once instead of disappearing.
 *
 * Both columns default to their "never abandoned" value so every existing
 * row (all currently either finalized or still legitimately provisional)
 * keeps behaving exactly as before until it actually fails to finalize.
 */
val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE quiz_attempts ADD COLUMN finalizeFailureCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE quiz_attempts ADD COLUMN abandoned INTEGER NOT NULL DEFAULT 0")
        }
    }
