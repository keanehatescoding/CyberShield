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
                ?: return@mapNotNull null

            Quiz(
                id           = doc.id,
                moduleId     = moduleId,
                text         = doc.getString("text") ?: "",
                options      = options,
                correctIndex = doc.getLong("correctIndex")?.toInt() ?: 0,
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