package com.example.cybershield.core.database.dao

import com.example.cybershield.core.database.entity.PlaybackPositionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPositionDaoTest : RoomDbTestBase() {
    private val dao get() = db.playbackPositionDao()

    private fun fakeEntity(
        moduleId: String = "module1",
        uid: String = "user1",
        positionMs: Long = 15_000L,
    ) = PlaybackPositionEntity(
        moduleId = moduleId,
        uid = uid,
        positionMs = positionMs,
    )

    @Test
    fun `getPosition returns null when no row exists`() =
        runTest {
            assertNull(dao.getPosition(moduleId = "module1", uid = "user1"))
        }

    @Test
    fun `upsert then getPosition returns stored position`() =
        runTest {
            dao.upsert(fakeEntity(positionMs = 42_000L))

            val result = dao.getPosition(moduleId = "module1", uid = "user1")

            assertEquals(42_000L, result)
        }

    @Test
    fun `upsert with REPLACE overwrites position for same composite key`() =
        runTest {
            dao.upsert(fakeEntity(positionMs = 1_000L))
            dao.upsert(fakeEntity(positionMs = 99_000L))

            assertEquals(99_000L, dao.getPosition(moduleId = "module1", uid = "user1"))
        }

    @Test
    fun `getPosition is scoped per moduleId and uid composite key`() =
        runTest {
            dao.upsert(fakeEntity(moduleId = "module1", uid = "user1", positionMs = 10_000L))
            dao.upsert(fakeEntity(moduleId = "module1", uid = "user2", positionMs = 20_000L))
            dao.upsert(fakeEntity(moduleId = "module2", uid = "user1", positionMs = 30_000L))

            assertEquals(10_000L, dao.getPosition("module1", "user1"))
            assertEquals(20_000L, dao.getPosition("module1", "user2"))
            assertEquals(30_000L, dao.getPosition("module2", "user1"))
            assertNull(dao.getPosition("module2", "user2"))
        }
}
