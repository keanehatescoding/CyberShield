package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.Quiz
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetQuizUseCase @Inject constructor(
    private val quizRepository: QuizRepository,
) {
    suspend operator fun invoke(moduleId: String): Flow<Result<List<Quiz>>> =
        quizRepository.getQuizzesForModule(moduleId)
}