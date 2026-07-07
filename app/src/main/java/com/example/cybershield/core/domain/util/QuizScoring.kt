package com.example.cybershield.core.domain.util

/**
 * Quiz scoring constants. Kept in one place because two call sites need the
 * exact same formula to agree: QuizViewModel scores an answer the moment
 * it's graded online, and QuizRepositoryImpl recomputes the same formula
 * later when finalizing an attempt that had offline (deferred-grading)
 * answers. If these ever drifted apart, a provisional score and its
 * finalized replacement could disagree for reasons that have nothing to do
 * with correctness.
 */
object QuizScoring {
    const val BASE_POINTS = 100
    const val SPEED_BONUS = 5
    const val PASS_PERCENTAGE = 70

    fun pointsFor(
        isCorrect: Boolean,
        timeRemaining: Int,
    ): Int = if (isCorrect) BASE_POINTS + (timeRemaining * SPEED_BONUS) else 0
}
