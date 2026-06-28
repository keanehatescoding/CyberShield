package com.example.cybershield.core.domain.repository

import com.example.cybershield.core.domain.model.Question
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface QuizRepository {

    /** Fetches all questions for a given module from Firestore (or Room cache). */
    suspend fun getQuizzesForModule(quizId: String): Flow<Result<List<Question>>>

    /** Returns the pass mark percentage for a quiz (default 70). */
    suspend fun getPassMark(quizId: String): Result<Int>

    /**
     * Persists a single answer result.
     * Writes to Room immediately; SyncQuizResultsWorker pushes to Firestore
     * when the device is online.
     */
    suspend fun saveQuizResult(
        userId:         String,
        quizId:         String,
        moduleId:       String,
        isCorrect:      Boolean,
        selectedAnswer: String,
    )
}