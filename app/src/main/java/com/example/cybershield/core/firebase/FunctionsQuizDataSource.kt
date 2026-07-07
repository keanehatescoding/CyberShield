package com.example.cybershield.core.firebase

import com.example.cybershield.core.domain.model.AnswerValidation
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** A single offline-cached answer waiting to be graded once connectivity returns. */
data class PendingAnswer(
    val localId: Long,
    val quizId: String,
    val questionId: String,
    val selectedIndex: Int,
    val answeredAt: Long,
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
    @Inject
    constructor(
        private val functions: FirebaseFunctions,
    ) {
        /** Online path — called immediately after the user taps an option, for instant feedback. */
        suspend fun validateAnswer(
            quizId: String,
            questionId: String,
            selectedIndex: Int,
            answeredAt: Long,
        ): AnswerValidation {
            val payload =
                hashMapOf(
                    "quizId" to quizId,
                    "questionId" to questionId,
                    "selectedIndex" to selectedIndex,
                    "answeredAt" to answeredAt,
                )
            val response =
                functions
                    .getHttpsCallable("validateAnswer")
                    .call(payload)
                    .await()

            @Suppress("UNCHECKED_CAST")
            val data = response.data as Map<String, Any?>
            return AnswerValidation(
                questionId = data["questionId"] as String,
                isCorrect = data["isCorrect"] as Boolean,
                correctIndex = (data["correctIndex"] as Number).toInt(),
                explanation = data["explanation"] as? String ?: "",
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
                            )
                        },
                )
            val response =
                functions
                    .getHttpsCallable("validateAnswersBatch")
                    .call(payload)
                    .await()

            @Suppress("UNCHECKED_CAST")
            val data = response.data as Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            val rawResults = data["results"] as List<Map<String, Any?>>

            // Results come back in the same order as the request.
            return pending.zip(rawResults).map { (p, r) ->
                val error = r["error"] as? String
                if (error != null) {
                    BatchAnswerResult(localId = p.localId, validation = null, error = error)
                } else {
                    BatchAnswerResult(
                        localId = p.localId,
                        validation =
                            AnswerValidation(
                                questionId = r["questionId"] as String,
                                isCorrect = r["isCorrect"] as Boolean,
                                correctIndex = (r["correctIndex"] as Number).toInt(),
                                explanation = r["explanation"] as? String ?: "",
                            ),
                        error = null,
                    )
                }
            }
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
