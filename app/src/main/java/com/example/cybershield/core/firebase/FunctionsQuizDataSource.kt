package com.example.cybershield.core.firebase

import com.example.cybershield.core.domain.model.AnswerValidation
import com.example.cybershield.core.domain.repository.QuizFinalizeResult
import com.example.cybershield.core.domain.util.Result
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** A single offline-cached answer waiting to be graded once connectivity returns. */
data class PendingAnswer(
    val localId: Long,
    val resultId: String,
    val quizId: String,
    val questionId: String,
    val selectedIndex: Int,
    val answeredAt: Long,
    val timeRemaining: Int,
)

/** Result of a batch validation call, keyed back to the local row it came from. */
data class BatchAnswerResult(
    val localId: Long,
    val validation: AnswerValidation?,
    val error: String?,
)

/**
 * Talks to the server-side grading functions. This is the ONLY place in the
 * app that ever learns whether an answer was correct — no code path derives
 * `isCorrect` locally anymore.
 */
@Singleton
class FunctionsQuizDataSource
    internal constructor(
        /**
         * Test seam: the actual Firebase HTTPS-callable invocation, injected
         * once at construction time (not a mutable `var`) so production code
         * can't reassign the transport after the singleton is built. Tests use
         * this internal constructor directly to supply a fake, since
         * `HttpsCallableReference.call` is a final Firebase member with an
         * inline extension that MockK can't stub directly.
         */
        private val httpsCallable: suspend (name: String, payload: Map<String, Any?>) -> HttpsCallableResult,
    ) {
        @Inject
        constructor(functions: FirebaseFunctions) : this(
            { name, payload -> functions.getHttpsCallable(name).call(payload).await() },
        )

        /** Online path — called immediately after the user taps an option, for instant feedback. */
        suspend fun validateAnswer(
            quizId: String,
            questionId: String,
            selectedIndex: Int,
            answeredAt: Long,
            resultId: String,
            timeRemaining: Int,
        ): AnswerValidation {
            val payload =
                hashMapOf(
                    "quizId" to quizId,
                    "questionId" to questionId,
                    "selectedIndex" to selectedIndex,
                    "answeredAt" to answeredAt,
                    "resultId" to resultId,
                    "timeRemaining" to timeRemaining,
                )
            val response =
                httpsCallable("validateAnswer", payload)

            val data = response.data.asCallableData("validateAnswer")
            return AnswerValidation(
                questionId = data.requireString("questionId", "validateAnswer"),
                isCorrect = data.requireBoolean("isCorrect", "validateAnswer"),
                correctIndex = data.requireInt("correctIndex", "validateAnswer"),
                explanation = data.optString("explanation") ?: "",
            )
        }

        /**
         * Offline-catch-up path — called by SyncQuizResultsWorker once the
         * device is back online, for every answer that was cached locally
         * without a known outcome. At most 100 per call (see functions/src/index.ts).
         */
        suspend fun validateAnswersBatch(pending: List<PendingAnswer>): List<BatchAnswerResult> {
            val payload =
                hashMapOf(
                    "answers" to
                        pending.map { p ->
                            hashMapOf(
                                "quizId" to p.quizId,
                                "questionId" to p.questionId,
                                "selectedIndex" to p.selectedIndex,
                                "answeredAt" to p.answeredAt,
                                "resultId" to p.resultId,
                                "timeRemaining" to p.timeRemaining,
                            )
                        },
                )
            val response =
                httpsCallable("validateAnswersBatch", payload)

            val data = response.data.asCallableData("validateAnswersBatch")
            val rawResults = data.requireMapList("results", "validateAnswersBatch")

            // Results come back in the same order as the request.
            return pending.zip(rawResults).map { (p, r) ->
                val error = r.optString("error")
                if (error != null) {
                    BatchAnswerResult(localId = p.localId, validation = null, error = error)
                } else {
                    BatchAnswerResult(
                        localId = p.localId,
                        validation =
                            AnswerValidation(
                                questionId = r.requireString("questionId", "validateAnswersBatch"),
                                isCorrect = r.requireBoolean("isCorrect", "validateAnswersBatch"),
                                correctIndex = r.requireInt("correctIndex", "validateAnswersBatch"),
                                explanation = r.optString("explanation") ?: "",
                            ),
                        error = null,
                    )
                }
            }
        }

        /**
         * Server-authoritative finalization: recomputes the attempt's score
         * from the server-graded quizResults and issues the certificate +
         * CyberDefender badge. The client never writes those, so they cannot
         * be forged. Returns the server's verdict.
         */
        suspend fun finalizeQuizAttempt(resultId: String): Result<QuizFinalizeResult> =
            try {
                val response =
                    httpsCallable("finalizeQuizAttemptFn", hashMapOf("resultId" to resultId))

                val data = response.data.asCallableData("finalizeQuizAttemptFn")
                Result.Success(
                    QuizFinalizeResult(
                        passed = data.requireBoolean("passed", "finalizeQuizAttemptFn"),
                        score = data.requireInt("score", "finalizeQuizAttemptFn"),
                        correctCount = data.requireInt("correctCount", "finalizeQuizAttemptFn"),
                        percentage = data.requireInt("percentage", "finalizeQuizAttemptFn"),
                        xpEarned = data.optInt("xpEarned"),
                        alreadyFinalized = data.optBoolean("alreadyFinalized"),
                    ),
                )
            } catch (e: FirebaseFunctionsException) {
                Result.Error(Exception(e.message ?: "finalize failed", e))
            } catch (e: Exception) {
                Result.Error(e)
            }

        companion object {
            /** Mirrors the 100-per-call limit enforced in functions/src/index.ts. */
            const val MAX_BATCH_SIZE = 100
        }
    }

/** True for the class of errors worth retrying (network/quota), false for permanent ones (bad input). */
fun FirebaseFunctionsException.isTransient(): Boolean =
    when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
        -> true
        else -> false
    }
