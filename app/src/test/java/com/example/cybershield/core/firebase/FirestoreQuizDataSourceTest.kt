package com.example.cybershield.core.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        correctIndex: Long?,
        order: Long?,
        text: String = "What is phishing?",
        explanation: String = "It's a social-engineering attack.",
        moduleName: String = "Module 1",
        title: String = "Quiz Title",
    ): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns id
        every { doc.get("options") } returns options
        every { doc.getLong("correctIndex") } returns correctIndex
        every { doc.getLong("order") } returns order
        every { doc.getString("text") } returns text
        every { doc.getString("explanation") } returns explanation
        every { doc.getString("moduleName") } returns moduleName
        every { doc.getString("title") } returns title
        return doc
    }

    // ── getQuizzesForModule ──────────────────────────────────────────

    @Test
    fun `getQuizzesForModule maps a well-formed question document`() =
        runTest {
            val doc =
                fakeQuestionDoc(
                    id = "q1",
                    options = listOf("A", "B", "C"),
                    correctIndex = 1L,
                    order = 0L,
                )
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(doc)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertEquals(1, result.size)
            val question = result.single()
            assertEquals("q1", question.id)
            assertEquals("quiz1", question.moduleId)
            assertEquals(listOf("A", "B", "C"), question.options)
            assertEquals(1, question.correctIndex)
            assertEquals(0, question.order)
            assertEquals("Module 1", question.moduleName)
            assertEquals("Quiz Title", question.quizTitle)
        }

    @Test
    fun `getQuizzesForModule falls back to default quizTitle when title is missing`() =
        runTest {
            val doc =
                fakeQuestionDoc(id = "q1", options = listOf("A", "B"), correctIndex = 0L, order = 0L, title = "CyberShield Quiz")
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(doc)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertEquals("CyberShield Quiz", result.single().quizTitle)
        }

    @Test
    fun `getQuizzesForModule skips documents with missing options`() =
        runTest {
            val bad = fakeQuestionDoc(id = "bad", options = null, correctIndex = 0L, order = 0L)
            val good = fakeQuestionDoc(id = "good", options = listOf("A", "B"), correctIndex = 0L, order = 1L)
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(bad, good)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertEquals(listOf("good"), result.map { it.id })
        }

    @Test
    fun `getQuizzesForModule skips documents with missing correctIndex`() =
        runTest {
            val bad = fakeQuestionDoc(id = "bad", options = listOf("A", "B"), correctIndex = null, order = 0L)
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(bad)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getQuizzesForModule skips documents with missing order`() =
        runTest {
            val bad = fakeQuestionDoc(id = "bad", options = listOf("A", "B"), correctIndex = 0L, order = null)
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(bad)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getQuizzesForModule skips documents where correctIndex is out of bounds`() =
        runTest {
            val tooHigh = fakeQuestionDoc(id = "high", options = listOf("A", "B"), correctIndex = 2L, order = 0L)
            val negative = fakeQuestionDoc(id = "neg", options = listOf("A", "B"), correctIndex = -1L, order = 1L)
            val ok = fakeQuestionDoc(id = "ok", options = listOf("A", "B"), correctIndex = 1L, order = 2L)
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns listOf(tooHigh, negative, ok)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getQuizzesForModule("quiz1")

            assertEquals(listOf("ok"), result.map { it.id })
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

    // ── uploadQuizResults ─────────────────────────────────────────────

    @Test
    fun `uploadQuizResults commits one batch write per result keyed by quizId_localId`() =
        runTest {
            val usersCollection = mockk<CollectionReference>()
            val userDoc = mockk<DocumentReference>()
            val resultsCollection = mockk<CollectionReference>()
            val resultDocRef = mockk<DocumentReference>()
            val batch = mockk<WriteBatch>()

            every { firestore.collection("users") } returns usersCollection
            every { usersCollection.document("user1") } returns userDoc
            every { userDoc.collection("quizResults") } returns resultsCollection
            every { resultsCollection.document("quiz1_42") } returns resultDocRef
            every { firestore.batch() } returns batch

            val dataSlot = slot<Map<String, Any?>>()
            every { batch.set(resultDocRef, capture(dataSlot)) } returns batch
            every { batch.commit() } returns completedTask(null)

            val result =
                QuizResultUpload(
                    localId = 42,
                    userId = "user1",
                    quizId = "quiz1",
                    moduleId = "module1",
                    isCorrect = true,
                    selectedAnswer = "A",
                    answeredAt = 123L,
                )

            dataSource.uploadQuizResults(listOf(result))

            verify(exactly = 1) { batch.set(resultDocRef, any<Map<String, Any?>>()) }
            verify(exactly = 1) { batch.commit() }
            assertEquals("quiz1", dataSlot.captured["quizId"])
            assertEquals("module1", dataSlot.captured["moduleId"])
            assertEquals(true, dataSlot.captured["isCorrect"])
            assertEquals("A", dataSlot.captured["selectedAnswer"])
            assertEquals(123L, dataSlot.captured["answeredAt"])
        }

    @Test
    fun `uploadQuizResults with empty list still commits an empty batch`() =
        runTest {
            val batch = mockk<WriteBatch>()
            every { firestore.batch() } returns batch
            every { batch.commit() } returns completedTask(null)

            dataSource.uploadQuizResults(emptyList())

            verify(exactly = 1) { batch.commit() }
        }
}
