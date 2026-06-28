package com.example.cybershield.core.firebase

import com.example.cybershield.core.domain.model.Question
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreQuizDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    suspend fun getQuizzesForModule(quizId: String): List<Question> {
        val questionDocs = firestore
            .collection("quizzes")
            .document(quizId)
            .collection("questions")
            .orderBy("order")
            .get()
            .await()


        return questionDocs.documents.mapNotNull { doc ->
            val options = (doc.get("options") as? List<*>)?.filterIsInstance<String>()
                ?: return@mapNotNull null
            val correctIndex = doc.getLong("correctIndex")?.toInt() ?: return@mapNotNull null
            val order = doc.getLong("order")?.toInt() ?: return@mapNotNull null
            if (correctIndex < 0 || correctIndex >= options.size) return@mapNotNull null

            Question(
                id = doc.id, moduleId = quizId,
                text = doc.getString("text") ?: "",
                options = options, correctIndex = correctIndex,
                explanation = doc.getString("explanation") ?: "",
                moduleName = doc.getString("moduleName") ?: "",
                order = order
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