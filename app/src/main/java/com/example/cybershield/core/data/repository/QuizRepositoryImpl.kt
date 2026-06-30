package com.example.cybershield.core.data.repository

import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.QuizEntity
import com.example.cybershield.core.database.entity.QuizResultEntity
import com.example.cybershield.core.domain.model.Question
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
class QuizRepositoryImpl
    @Inject
    constructor(
        private val remoteSource: FirestoreQuizDataSource,
        private val quizDao: QuizDao,
        private val resultDao: QuizResultDao,
    ) : QuizRepository {
        override suspend fun getQuizzesForModule(quizId: String): Flow<Result<List<Question>>> =
            flow {
                emit(Result.Loading)
                try {
                    val remote = remoteSource.getQuizzesForModule(quizId)
                    if (remote.isNotEmpty()) {
                        quizDao.insertAll(remote.map { it.toEntity() })
                        emit(Result.Success(remote))
                    } else {
                        val cached = quizDao.getQuizzesForModule(quizId).map { it.toDomain() }
                        emit(Result.Success(cached))
                    }
                } catch (e: Exception) {
                    val cached = quizDao.getQuizzesForModule(quizId).map { it.toDomain() }
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
                } catch (_: Exception) {
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
                ),
            )
        }
    }

// ── Mapping extensions ────────────────────────────────────────────────

private fun Question.toEntity() =
    QuizEntity(
        id = id,
        moduleId = moduleId,
        text = text,
        options = options,
        correctIndex = correctIndex,
        explanation = explanation,
        moduleName = moduleName,
        order = order,
        quizTitle = quizTitle,
    )

private fun QuizEntity.toDomain() =
    Question(
        id = id,
        moduleId = moduleId,
        text = text,
        options = options,
        correctIndex = correctIndex,
        explanation = explanation,
        moduleName = moduleName,
        order = order,
        quizTitle = quizTitle,
    )
