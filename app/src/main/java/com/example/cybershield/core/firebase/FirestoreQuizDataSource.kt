package com.example.cybershield.core.firebase

import com.example.cybershield.core.domain.model.Question
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain data-layer DTO for a locally-recorded quiz result awaiting upload.
 * Kept separate from QuizResultEntity so this Firestore-facing class doesn't
 * depend on the Room entity shape.
 */
data class QuizResultUpload(
    val localId: Long,
    val userId: String,
    val quizId: String,
    val moduleId: String,
    val isCorrect: Boolean,
    val selectedAnswer: String,
    val answeredAt: Long,
)

@Singleton
class FirestoreQuizDataSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
    ) {
        suspend fun getQuizzesForModule(quizId: String): List<Question> {
            val questionDocs =
                firestore
                    .collection("quizzes")
                    .document(quizId)
                    .collection("questions")
                    .orderBy("order")
                    .get()
                    .await()
            return questionDocs.documents.mapNotNull { doc ->
                val options =
                    (doc.get("options") as? List<*>)?.filterIsInstance<String>()
                        ?: return@mapNotNull null
                val correctIndex = doc.getLong("correctIndex")?.toInt() ?: return@mapNotNull null
                val order = doc.getLong("order")?.toInt() ?: return@mapNotNull null
                if (correctIndex < 0 || correctIndex >= options.size) return@mapNotNull null
                Question(
                    id = doc.id,
                    moduleId = quizId,
                    text = doc.getString("text") ?: "",
                    options = options,
                    correctIndex = correctIndex,
                    explanation = doc.getString("explanation") ?: "",
                    moduleName = doc.getString("moduleName") ?: "",
                    quizTitle = doc.getString("title") ?: "CyberShield Quiz",
                    order = order,
                )
            }
        }

        suspend fun getPassMark(quizId: String): Int =
            firestore
                .collection("quizzes")
                .document(quizId)
                .get()
                .await()
                .getLong("passMark")
                ?.toInt() ?: 70

        /**
         * Commits a single Firestore batch for [results]. Callers are
         * responsible for chunking (Firestore batches cap at 500 writes) and
         * for marking rows synced only after this returns successfully.
         */
        suspend fun uploadQuizResults(results: List<QuizResultUpload>) {
            val batch = firestore.batch()
            results.forEach { result ->
                val ref =
                    firestore
                        .collection("users")
                        .document(result.userId)
                        .collection("quizResults")
                        .document("${result.quizId}_${result.localId}")
                batch.set(
                    ref,
                    mapOf(
                        "quizId" to result.quizId,
                        "moduleId" to result.moduleId,
                        "isCorrect" to result.isCorrect,
                        "selectedAnswer" to result.selectedAnswer,
                        "answeredAt" to result.answeredAt,
                        "syncedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            batch.commit().await()
        }
    }
