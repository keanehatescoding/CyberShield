package com.example.cybershield.core.database.dao

import com.example.cybershield.core.database.entity.QuizEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizDaoTest : RoomDbTestBase() {
    private val dao get() = db.quizDao()

    private fun fakeQuestion(
        id: String,
        moduleId: String = "module1",
        options: List<String> = listOf("A", "B", "C", "D"),
    ) = QuizEntity(
        id = id,
        moduleId = moduleId,
        text = "Question $id",
        options = options,
        order = 1,
        moduleName = "Phishing Basics",
        quizTitle = "Phishing Quiz",
    )

    @Test
    fun `getQuizzesForModule returns only matching moduleId`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeQuestion(id = "q1", moduleId = "module1"),
                    fakeQuestion(id = "q2", moduleId = "module1"),
                    fakeQuestion(id = "q3", moduleId = "module2"),
                ),
            )

            val result = dao.getQuizzesForModule("module1")

            assertEquals(setOf("q1", "q2"), result.map { it.id }.toSet())
        }

    @Test
    fun `getQuizzesForModule returns empty list when no match`() =
        runTest {
            assertTrue(dao.getQuizzesForModule("nonexistent").isEmpty())
        }

    @Test
    fun `options list survives the TypeConverter round trip`() =
        runTest {
            val options = listOf("Option A", "Option B||with weird text", "Option C")
            dao.insertAll(listOf(fakeQuestion(id = "q1", options = options)))

            val result = dao.getQuizzesForModule("module1").single()

            // Converters now serializes/deserializes via kotlinx.serialization JSON rather than
            // a "||"-joined string, so options containing "||" round-trip exactly as stored.
            assertEquals(options, result.options)
        }

    @Test
    fun `empty options list round trips to empty list`() =
        runTest {
            dao.insertAll(listOf(fakeQuestion(id = "q1", options = emptyList())))

            val result = dao.getQuizzesForModule("module1").single()

            assertTrue(result.options.isEmpty())
        }

    @Test
    fun `deleteForModule removes only that module's quizzes`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeQuestion(id = "q1", moduleId = "module1"),
                    fakeQuestion(id = "q2", moduleId = "module2"),
                ),
            )

            dao.deleteForModule("module1")

            assertTrue(dao.getQuizzesForModule("module1").isEmpty())
            assertEquals(1, dao.getQuizzesForModule("module2").size)
        }

    @Test
    fun `clearAll removes every quiz regardless of module`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeQuestion(id = "q1", moduleId = "module1"),
                    fakeQuestion(id = "q2", moduleId = "module2"),
                ),
            )

            dao.clearAll()

            assertTrue(dao.getQuizzesForModule("module1").isEmpty())
            assertTrue(dao.getQuizzesForModule("module2").isEmpty())
        }

    @Test
    fun `insertAll with REPLACE overwrites existing row on same id`() =
        runTest {
            dao.insertAll(listOf(fakeQuestion(id = "q1", moduleId = "module1")))
            dao.insertAll(listOf(fakeQuestion(id = "q1", moduleId = "module2")))

            assertTrue(dao.getQuizzesForModule("module1").isEmpty())
            assertEquals(1, dao.getQuizzesForModule("module2").size)
        }
}
