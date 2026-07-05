package com.example.cybershield.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.cybershield.core.database.dao.QuizAttemptDao
import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.QuizAttemptEntity
import com.example.cybershield.core.database.entity.QuizResultEntity
import com.example.cybershield.core.database.entity.toEntity
import com.example.cybershield.core.domain.model.AnswerValidation
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResult
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.resultOf
import com.example.cybershield.core.firebase.FirestoreQuizDataSource
import com.example.cybershield.core.firebase.FunctionsQuizDataSource
import com.example.cybershield.core.firebase.PendingAnswer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizRepositoryImpl
    @Inject
    constructor(
        private val remoteSource: FirestoreQuizDataSource,
        private val functionsSource: FunctionsQuizDataSource,
        private val quizDao: QuizDao,
        private val quizAttemptDao: QuizAttemptDao,
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val cached = quizDao.getQuizzesForModule(quizId).map { it.toDomain() }
                    if (cached.isNotEmpty()) {
                        emit(Result.Success(cached))
                    } else {
                        emit(Result.Error(e))
                    }
                }
            }

        override fun getQuizResultHistory(userId: String): Flow<PagingData<QuizResultHistoryItem>> =
            Pager(
                config = PagingConfig(pageSize = HISTORY_PAGE_SIZE, enablePlaceholders = false),
                pagingSourceFactory = { resultDao.getResultsForUserPaged(userId) },
            ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

        override suspend fun getPassMark(quizId: String): Result<Int> =
            withContext(Dispatchers.IO) {
                try {
                    Result.Success(remoteSource.getPassMark(quizId))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Result.Success(70) // safe default — not an error worth surfacing
                }
            }

        override suspend fun validateAnswerOnline(
            userId: String,
            quizId: String,
            questionId: String,
            selectedIndex: Int,
            selectedAnswer: String,
            moduleId: String,
        ): Result<AnswerValidation> =
            withContext(Dispatchers.IO) {
                resultOf {
                    val answeredAt = System.currentTimeMillis()
                    val validation = functionsSource.validateAnswer(quizId, questionId, selectedIndex, answeredAt)

                    // Cache the already-graded result locally too, so quiz
                    // history works offline and doesn't need a second round trip.
                    resultDao.insert(
                        QuizResultEntity(
                            userId = userId,
                            quizId = quizId,
                            questionId = questionId,
                            moduleId = moduleId,
                            isCorrect = validation.isCorrect,
                            selectedIndex = selectedIndex,
                            selectedAnswer = selectedAnswer,
                            explanation = validation.explanation,
                            answeredAt = answeredAt,
                            synced = true,
                        ),
                    )

                    Result.Success(validation)
                }
            }

        override suspend fun cachePendingAnswer(
            userId: String,
            quizId: String,
            questionId: String,
            moduleId: String,
            selectedIndex: Int,
            selectedAnswer: String,
        ) = withContext(Dispatchers.IO) {
            resultDao.insert(
                QuizResultEntity(
                    userId = userId,
                    quizId = quizId,
                    questionId = questionId,
                    moduleId = moduleId,
                    isCorrect = null, // unknown until validateAnswersBatch grades it
                    selectedIndex = selectedIndex,
                    selectedAnswer = selectedAnswer,
                    explanation = null,
                    answeredAt = System.currentTimeMillis(),
                    synced = false,
                ),
            )
            Unit
        }

        override suspend fun syncPendingResults(): Result<Unit> =
            withContext(Dispatchers.IO) {
                resultOf {
                    val pending = resultDao.getPendingResults()
                    if (pending.isEmpty()) return@resultOf Result.Success(Unit)

                    pending.chunked(FunctionsQuizDataSource.MAX_BATCH_SIZE).forEach { chunk ->
                        val batchResults =
                            functionsSource.validateAnswersBatch(
                                chunk.map { entity ->
                                    PendingAnswer(
                                        localId = entity.localId,
                                        quizId = entity.quizId,
                                        questionId = entity.questionId,
                                        selectedIndex = entity.selectedIndex,
                                        answeredAt = entity.answeredAt,
                                    )
                                },
                            )
                        // Grade and mark-synced per row, so a row the server
                        // rejected (e.g. its question was deleted) is simply
                        // left pending rather than silently dropped or
                        // incorrectly marked as graded.
                        batchResults.forEach { result ->
                            val validation = result.validation
                            if (validation != null) {
                                resultDao.markGraded(
                                    localId = result.localId,
                                    isCorrect = validation.isCorrect,
                                    explanation = validation.explanation,
                                )
                            }
                        }
                    }
                    Result.Success(Unit)
                }
            }

        override suspend fun saveQuizAttempt(
            resultId: String,
            result: QuizResult,
        ) {
            quizAttemptDao.insert(
                QuizAttemptEntity(
                    resultId = resultId,
                    quizId = result.quizId,
                    score = result.score,
                    totalQuestions = result.totalQuestions,
                    correctCount = result.correctCount,
                    percentage = result.percentage,
                    xpEarned = result.xpEarned,
                    passed = result.passed,
                    timeTaken = result.timeTaken,
                    createdAt = System.currentTimeMillis(),
                    provisional = result.provisional,
                ),
            )
        }

        override suspend fun getQuizAttempt(resultId: String): QuizResult? =
            quizAttemptDao.getById(resultId)?.let {
                QuizResult(
                    quizId = it.quizId,
                    score = it.score,
                    totalQuestions = it.totalQuestions,
                    correctCount = it.correctCount,
                    percentage = it.percentage,
                    xpEarned = it.xpEarned,
                    passed = it.passed,
                    timeTaken = it.timeTaken,
                    provisional = it.provisional,
                )
            }

        private companion object {
            const val HISTORY_PAGE_SIZE = 20
        }
    }
