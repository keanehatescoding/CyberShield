package com.example.cybershield.core.database.dao

import com.example.cybershield.core.database.entity.ModuleEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

class ModuleDaoTest : RoomDbTestBase() {
    private val dao get() = db.moduleDao()

    private fun fakeModule(
        id: String,
        order: Int,
        published: Boolean = true,
    ) = ModuleEntity(
        id = id,
        title = "Module $id",
        description = "desc",
        thumbnailUrl = null,
        videoUrl = "https://example.com/$id.mp4",
        category = "phishing",
        durationMins = 5,
        order = order,
        xpReward = 10,
        quizId = "quiz_$id",
        published = published,
        new = false,
    )

    @Test
    fun `getAll returns only published modules ordered by order ascending`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeModule(id = "m3", order = 3, published = true),
                    fakeModule(id = "m1", order = 1, published = true),
                    fakeModule(id = "m2", order = 2, published = false), // unpublished, excluded
                ),
            )

            val result = dao.getAll()

            Assert.assertEquals(listOf("m1", "m3"), result.map { it.id })
        }

    @Test
    fun `getAll returns empty list when no modules published`() =
        runTest {
            dao.insertAll(listOf(fakeModule(id = "m1", order = 1, published = false)))

            Assert.assertTrue(dao.getAll().isEmpty())
        }

    @Test
    fun `getById returns matching module`() =
        runTest {
            dao.insertAll(listOf(fakeModule(id = "m1", order = 1)))

            val result = dao.getById("m1")

            Assert.assertEquals("m1", result?.id)
        }

    @Test
    fun `getById returns null when not found`() =
        runTest {
            Assert.assertNull(dao.getById("missing"))
        }

    @Test
    fun `insertAll with REPLACE overwrites existing row on same id`() =
        runTest {
            dao.insertAll(listOf(fakeModule(id = "m1", order = 1)))
            dao.insertAll(listOf(fakeModule(id = "m1", order = 99)))

            val result = dao.getById("m1")

            Assert.assertEquals(99, result?.order)
            Assert.assertEquals(1, dao.getAll().size)
        }

    @Test
    fun `clearAll removes all rows`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeModule(id = "m1", order = 1),
                    fakeModule(id = "m2", order = 2),
                ),
            )

            dao.clearAll()

            Assert.assertTrue(dao.getAll().isEmpty())
            Assert.assertNull(dao.getById("m1"))
        }

    @Test
    fun `replaceAll clears existing rows then inserts new set`() =
        runTest {
            dao.insertAll(
                listOf(
                    fakeModule(id = "old1", order = 1),
                    fakeModule(id = "old2", order = 2),
                ),
            )

            dao.replaceAll(listOf(fakeModule(id = "new1", order = 1)))

            val result = dao.getAll()
            Assert.assertEquals(listOf("new1"), result.map { it.id })
        }

    @Test
    fun `replaceAll with empty list clears table and leaves it empty`() =
        runTest {
            dao.insertAll(listOf(fakeModule(id = "m1", order = 1)))

            dao.replaceAll(emptyList())

            Assert.assertTrue(dao.getAll().isEmpty())
        }
}
