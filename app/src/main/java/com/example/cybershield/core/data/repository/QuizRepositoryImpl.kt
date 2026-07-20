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
import com.example.cybershield.core.domain.model.ReadyToFinalizeAttempt
import com.example.cybershield.core.domain.repository.QuizFinalizeResult
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.CrashReporter
import com.example.cybershield.core.domain.util.QuizScoring
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
        private val crashReporter: CrashReporter,
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
                } catch (e: Exception) {
                    // Falling back rather than surfacing an error keeps the quiz
                    // usable offline/on a flaky connection, but a fetch failure
                    // still deserves a signal — record it instead of dropping it.
                    crashReporter.recordException(e, mapOf("quizId" to quizId))
                    Result.Success(QuizScoring.PASS_PERCENTAGE) // safe default, kept in sync with the server's PASS_PERCENTAGE
                }
            }

        override suspend fun validateAnswerOnline(
            userId: String,
            resultId: String,
            quizId: String,
            questionId: String,
            selectedIndex: Int,
            selectedAnswer: String,
            moduleId: String,
            timeRemaining: Int,
        ): Result<AnswerValidation> =
            withContext(Dispatchers.IO) {
                resultOf {
                    val answeredAt = System.currentTimeMillis()
                    val validation =
                        functionsSource.validateAnswer(quizId, questionId, selectedIndex, answeredAt, resultId, timeRemaining)

                    // Cache the already-graded result locally too, so quiz
                    // history works offline and doesn't need a second round trip.
                    resultDao.insert(
                        QuizResultEntity(
                            resultId = resultId,
                            userId = userId,
                            quizId = quizId,
                            questionId = questionId,
                            moduleId = moduleId,
                            isCorrect = validation.isCorrect,
                            selectedIndex = selectedIndex,
                            selectedAnswer = selectedAnswer,
                            explanation = validation.explanation,
                            answeredAt = answeredAt,
                            timeRemaining = timeRemaining,
                            synced = true,
                        ),
                    )

                    Result.Success(validation)
                }
            }

        override suspend fun cachePendingAnswer(
            userId: String,
            resultId: String,
            quizId: String,
            questionId: String,
            moduleId: String,
            selectedIndex: Int,
            selectedAnswer: String,
            timeRemaining: Int,
        ) = withContext(Dispatchers.IO) {
            resultDao.insert(
                QuizResultEntity(
                    resultId = resultId,
                    userId = userId,
                    quizId = quizId,
                    questionId = questionId,
                    moduleId = moduleId,
                    isCorrect = null, // unknown until validateAnswersBatch grades it
                    selectedIndex = selectedIndex,
                    selectedAnswer = selectedAnswer,
                    explanation = null,
                    answeredAt = System.currentTimeMillis(),
                    timeRemaining = timeRemaining,
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
                                        resultId = entity.resultId,
                                        quizId = entity.quizId,
                                        questionId = entity.questionId,
                                        selectedIndex = entity.selectedIndex,
                                        answeredAt = entity.answeredAt,
                                        timeRemaining = entity.timeRemaining,
                                    )
                                },
                            )
                        // Grade and mark-synced per row. A row the server
                        // rejected (e.g. its question was deleted server-side)
                        // is marked synced-without-verdict so it stops blocking
                        // the parent attempt from finalizing — otherwise the
                        // whole session could never be scored and the user
                        // would lose all XP / certificate for it.
                        batchResults.forEach { result ->
                            val validation = result.validation
                            if (validation != null) {
                                resultDao.markGraded(
                                    localId = result.localId,
                                    isCorrect = validation.isCorrect,
                                    explanation = validation.explanation,
                                )
                            } else {
                                resultDao.markSyncFailed(localId = result.localId)
                            }
                        }
                    }
                    Result.Success(Unit)
                }
            }

        override suspend fun saveQuizAttempt(
            resultId: String,
            userId: String,
            moduleId: String,
            moduleName: String,
            quizTitle: String,
            result: QuizResult,
        ) {
            quizAttemptDao.insert(
                QuizAttemptEntity(
                    resultId = resultId,
                    userId = userId,
                    quizId = result.quizId,
                    moduleId = moduleId,
                    moduleName = moduleName,
                    quizTitle = quizTitle,
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

        override suspend fun getAttemptsReadyToFinalize(): List<ReadyToFinalizeAttempt> =
            withContext(Dispatchers.IO) {
                quizAttemptDao.getProvisionalAttempts().mapNotNull { attempt ->
                    // Zero means every answer in this attempt now has a server
                    // verdict — anything else means it's still waiting on sync.
                    if (resultDao.countUnsyncedForAttempt(attempt.resultId) > 0) return@mapNotNull null

                    val answers = resultDao.getResultsForAttempt(attempt.resultId)
                    if (answers.isEmpty()) return@mapNotNull null // shouldn't happen, but don't finalize off nothing

                    // Recompute score/correctCount from the now-fully-graded rows
                    // rather than trusting QuizViewModel's in-session numbers,
                    // which never included points for answers that were still
                    // pending when the quiz screen showed its (provisional) summary.
                    val correctCount = answers.count { it.isCorrect == true }
                    val score =
                        answers.sumOf { answer ->
                            QuizScoring.pointsFor(
                                isCorrect = answer.isCorrect == true,
                                timeRemaining = answer.timeRemaining,
                            )
                        }

                    ReadyToFinalizeAttempt(
                        resultId = attempt.resultId,
                        userId = attempt.userId,
                        quizId = attempt.quizId,
                        moduleId = attempt.moduleId,
                        moduleName = attempt.moduleName,
                        quizTitle = attempt.quizTitle,
                        score = score,
                        totalQuestions = answers.size,
                        correctCount = correctCount,
                    )
                }
            }

        override suspend fun finalizeAttempt(
            resultId: String,
            score: Int,
            correctCount: Int,
            percentage: Int,
            xpEarned: Int,
            passed: Boolean,
        ) = withContext(Dispatchers.IO) {
            quizAttemptDao.finalize(
                resultId = resultId,
                score = score,
                correctCount = correctCount,
                percentage = percentage,
                xpEarned = xpEarned,
                passed = passed,
            )
        }

        override suspend fun finalizeQuizAttemptServer(resultId: String): Result<QuizFinalizeResult> =
            withContext(Dispatchers.IO) {
                functionsSource.finalizeQuizAttempt(resultId)
            }

        override suspend fun recordFinalizeFailure(resultId: String): Boolean =
            withContext(Dispatchers.IO) {
                val current = quizAttemptDao.getById(resultId) ?: return@withContext false
                val newCount = current.finalizeFailureCount + 1
                val shouldAbandon = newCount >= MAX_FINALIZE_FAILURES
                quizAttemptDao.updateFinalizeFailure(
                    resultId = resultId,
                    count = newCount,
                    abandoned = shouldAbandon,
                )
                shouldAbandon
            }

        private companion object {
            const val HISTORY_PAGE_SIZE = 20

            // A handful of retries covers transient failures (network blip,
            // Firestore quota, a brief functions outage). Beyond that, it's most
            // likely permanent — e.g. the user retook this quiz before the
            // attempt synced, and the retake's answers overwrote this attempt's
            // quizResults docs server-side — so there's no point retrying forever.
            const val MAX_FINALIZE_FAILURES = 5
        }
    }
