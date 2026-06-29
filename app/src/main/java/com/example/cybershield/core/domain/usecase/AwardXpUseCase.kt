package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import javax.inject.Inject

private const val XP_PER_CORRECT_ANSWER = 10
private const val XP_BONUS_PERFECT_SCORE = 50

/**
 * Awards XP to the user after a quiz attempt.
 * Grants XP_PER_CORRECT_ANSWER per correct answer, plus a bonus
 * for a perfect score. Returns the total XP awarded.
 */
class AwardXpUseCase
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) {
        suspend operator fun invoke(
            userId: String,
            correctCount: Int,
            totalCount: Int,
        ): Result<Int> =
            try {
                val baseXp = correctCount * XP_PER_CORRECT_ANSWER
                val bonusXp = if (correctCount == totalCount) XP_BONUS_PERFECT_SCORE else 0
                val totalXp = baseXp + bonusXp

                userRepository.addXp(uid = userId, points = totalXp)

                Result.Success(totalXp)
            } catch (e: Exception) {
                Result.Error(e)
            }
    }
