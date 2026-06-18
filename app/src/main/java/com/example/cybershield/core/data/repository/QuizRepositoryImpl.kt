package com.example.cybershield.core.data.repository

import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.QuizEntity
import com.example.cybershield.core.database.entity.QuizResultEntity
import com.example.cybershield.core.domain.model.Quiz
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.firebase.FirestoreQuizDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizRepositoryImpl @Inject constructor(
    private val remoteSource: FirestoreQuizDataSource,
    private val quizDao: QuizDao,
    private val resultDao: QuizResultDao,
) : QuizRepository {

    override suspend fun getQuizzesForModule(moduleId: String): Flow<Result<List<Quiz>>> = flow {
        emit(Result.Loading)
        try {
            val remote = remoteSource.getQuizzesForModule(moduleId)
            if (remote.isNotEmpty()) {
                quizDao.insertAll(remote.map { it.toEntity() })
                emit(Result.Success(remote))
            } else {
                val cached = quizDao.getQuizzesForModule(moduleId).map { it.toDomain() }
                emit(Result.Success(cached))
            }
        } catch (e: Exception) {
            val cached = quizDao.getQuizzesForModule(moduleId).map { it.toDomain() }
            if (cached.isNotEmpty()) {
                emit(Result.Success(cached))
            } else {
                emit(Result.Error(e))
            }
        }
    }

    override suspend fun getPassMark(quizId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                Result.Success(remoteSource.getPassMark(quizId))
            } catch (e: Exception) {
                Result.Success(70) // safe default — not an error worth surfacing
            }
        }

    override suspend fun saveQuizResult(
        userId: String,
        quizId: String,
        moduleId: String,
        isCorrect: Boolean,
        selectedAnswer: String,
    ) = withContext(Dispatchers.IO) {
        resultDao.insert(
            QuizResultEntity(
                userId = userId,
                quizId = quizId,
                moduleId = moduleId,
                isCorrect = isCorrect,
                selectedAnswer = selectedAnswer,
                answeredAt = System.currentTimeMillis(),
                synced = false,
            )
        )
    }
}

// ── Mapping extensions ────────────────────────────────────────────────

private fun Quiz.toEntity() = QuizEntity(
    id = id,
    moduleId = moduleId,
    text = text,
    options = options,
    correctIndex = correctIndex,
    explanation = explanation,
)

private fun QuizEntity.toDomain() = Quiz(
    id = id,
    moduleId = moduleId,
    text = text,
    options = options,
    correctIndex = correctIndex,
    explanation = explanation,
)