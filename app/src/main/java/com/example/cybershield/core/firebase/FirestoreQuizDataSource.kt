package com.example.cybershield.core.firebase

import com.example.cybershield.core.domain.model.Quiz
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreQuizDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    suspend fun getQuizzesForModule(moduleId: String): List<Quiz> {
        val docs = firestore
            .collection("quizzes")
            .whereEqualTo("moduleId", moduleId)
            .get()
            .await()

        return docs.documents.mapNotNull { doc ->
            val options = (doc.get("options") as? List<*>)
                ?.filterIsInstance<String>()
                ?: run {
                    // ★ NEW — log and skip rather than silently producing a broken question
                    android.util.Log.e("FirestoreQuizDataSource",
                        "Quiz ${doc.id} missing or malformed 'options' field — skipping")
                    return@mapNotNull null
                }
            val correctIndexRaw = doc.getLong("correctIndex")
            if (correctIndexRaw == null) {
                android.util.Log.e("FirestoreQuizDataSource",
                    "Quiz ${doc.id} missing 'correctIndex' field — skipping question entirely")
                return@mapNotNull null
            }
            val correctIndex = correctIndexRaw.toInt()
            if (correctIndex < 0 || correctIndex >= options.size) {
                android.util.Log.e("FirestoreQuizDataSource",
                    "Quiz ${doc.id} has correctIndex=$correctIndex out of bounds for ${options.size} options — skipping")
                return@mapNotNull null
            }


            Quiz(
                id           = doc.id,
                moduleId     = moduleId,
                text         = doc.getString("text") ?: "",
                options      = options,
                correctIndex = correctIndex,
                explanation  = doc.getString("explanation") ?: "",
            )
        }
    }

    suspend fun getPassMark(quizId: String): Int =
        firestore
            .collection("quizzes")
            .document(quizId)
            .get()
            .await()
            .getLong("passMark")?.toInt() ?: 70
}