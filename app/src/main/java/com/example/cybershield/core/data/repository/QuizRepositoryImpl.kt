package com.example.cybershield.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.cybershield.core.database.dao.QuizDao
import com.example.cybershield.core.database.dao.QuizResultDao
import com.example.cybershield.core.database.entity.QuizResultEntity
import com.example.cybershield.core.database.entity.toEntity
import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.model.QuizResultHistoryItem
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.resultOf
import com.example.cybershield.core.firebase.FirestoreQuizDataSource
import com.example.cybershield.core.firebase.QuizResultUpload
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

        override suspend fun syncPendingResults(): Result<Unit> =
            withContext(Dispatchers.IO) {
                resultOf {
                    val pending = resultDao.getPendingResults()
                    if (pending.isEmpty()) return@resultOf Result.Success(Unit)

                    pending.chunked(SYNC_CHUNK_SIZE).forEach { chunk ->
                        remoteSource.uploadQuizResults(
                            chunk.map { entity ->
                                QuizResultUpload(
                                    localId = entity.localId,
                                    userId = entity.userId,
                                    quizId = entity.quizId,
                                    moduleId = entity.moduleId,
                                    isCorrect = entity.isCorrect,
                                    selectedAnswer = entity.selectedAnswer,
                                    answeredAt = entity.answeredAt,
                                )
                            },
                        )
                        // Mark and delete per-chunk so a later chunk's failure
                        // doesn't force re-upload of chunks that already
                        // committed successfully.
                        resultDao.markSyncedAndDelete(chunk.map { it.localId })
                    }
                    Result.Success(Unit)
                }
            }

        private companion object {
            /** Firestore batch writes cap at 500; stay comfortably under it. */
            const val SYNC_CHUNK_SIZE = 450
            const val HISTORY_PAGE_SIZE = 20
        }
    }
