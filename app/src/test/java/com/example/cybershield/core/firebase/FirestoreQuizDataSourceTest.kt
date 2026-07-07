package com.example.cybershield.core.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * NOTE: this data source is now read-only (question text/options/order and
 * pass mark). Grading and quizResults persistence moved entirely to
 * validateAnswer / validateAnswersBatch — see FunctionsQuizDataSourceTest —
 * so correctIndex/explanation and uploadQuizResults no longer exist here.
 */
class FirestoreQuizDataSourceTest {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var dataSource: FirestoreQuizDataSource

    private lateinit var quizzesCollection: CollectionReference
    private lateinit var quizDoc: DocumentReference
    private lateinit var questionsCollection: CollectionReference
    private lateinit var orderedQuery: Query

    @Before
    fun setUp() {
        firestore = mockk()
        dataSource = FirestoreQuizDataSource(firestore)

        quizzesCollection = mockk()
        quizDoc = mockk()
        questionsCollection = mockk()
        orderedQuery = mockk()

        every { firestore.collection("quizzes") } returns quizzesCollection
        every { quizzesCollection.document(any()) } returns quizDoc
        every { quizDoc.collection("questions") } returns questionsCollection
        every { questionsCollection.orderBy("order") } returns orderedQuery
    }

    private fun <T> completedTask(value: T): Task<T> {
        val task = mockk<Task<T>>()
        every { task.isComplete } returns true
        every { task.isCanceled } returns false
        every { task.exception } returns null
        every { task.result } returns value
        return task
    }

    private fun fakeQuestionDoc(
        id: String,
        options: List<String>?,
        order: Long?,
        text: String = "What is phishing?",
        moduleName: String = "Module 1",
        title: String = "Quiz Title",
    ): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns id
        every { doc.get("options") } returns options
        every { doc.getLong("order") } returns order
        every { doc.getString("text") } returns text
        every { doc.getString("moduleName") } returns moduleName
        every { doc.getString("title") } returns title
        return doc
    }

    // ── getQuizzesForModule ──────────────────────────────────────────

    @Test
    fun `getQuizzesForModule maps a well-formed question document`() =
        runTest {
            val doc = fakeQuestionDoc(id = "q1", options = listOf("A", "B", "C"), order = 0L)
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(doc)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertEquals(1, result.size)
            val question = result.single()
            assertEquals("q1", question.id)
            assertEquals("quiz1", question.moduleId)
            assertEquals(listOf("A", "B", "C"), question.options)
            assertEquals(0, question.order)
            assertEquals("Module 1", question.moduleName)
            assertEquals("Quiz Title", question.quizTitle)
        }

    @Test
    fun `getQuizzesForModule never exposes an answer key field`() =
        runTest {
            // Regression guard: Question has no correctIndex/explanation
            // property at all anymore, so there's nothing to assert absent
            // on the *result* — this documents the intent for future readers.
            val doc = fakeQuestionDoc(id = "q1", options = listOf("A", "B"), order = 0L)
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(doc)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1").single()

            // Compiles only because Question has no correctIndex/explanation
            // fields; if someone re-adds either, this test file (and the
            // fixture above) needs to be revisited deliberately.
            assertEquals("q1", result.id)
        }

    @Test
    fun `getQuizzesForModule falls back to default quizTitle when title is missing`() =
        runTest {
            val doc = fakeQuestionDoc(id = "q1", options = listOf("A", "B"), order = 0L, title = "CyberShield Quiz")
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(doc)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertEquals("CyberShield Quiz", result.single().quizTitle)
        }

    @Test
    fun `getQuizzesForModule skips documents with missing options`() =
        runTest {
            val bad = fakeQuestionDoc(id = "bad", options = null, order = 0L)
            val good = fakeQuestionDoc(id = "good", options = listOf("A", "B"), order = 1L)
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(bad, good)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertEquals(listOf("good"), result.map { it.id })
        }

    @Test
    fun `getQuizzesForModule skips documents with missing order`() =
        runTest {
            val bad = fakeQuestionDoc(id = "bad", options = listOf("A", "B"), order = null)
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(bad)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getQuizzesForModule returns empty list for empty snapshot`() =
        runTest {
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns emptyList()
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertTrue(result.isEmpty())
        }

    // ── getPassMark ───────────────────────────────────────────────────

    @Test
    fun `getPassMark returns stored value when present`() =
        runTest {
            val doc = mockk<DocumentSnapshot>()
            every { doc.getLong("passMark") } returns 80L
            every { quizDoc.get() } returns completedTask(doc)

            val result = dataSource.getPassMark("quiz1")

            assertEquals(80, result)
        }

    @Test
    fun `getPassMark defaults to 70 when field is missing`() =
        runTest {
            val doc = mockk<DocumentSnapshot>()
            every { doc.getLong("passMark") } returns null
            every { quizDoc.get() } returns completedTask(doc)

            val result = dataSource.getPassMark("quiz1")

            assertEquals(70, result)
        }
}
