package com.example.cybershield.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.cybershield.core.database.CyberShieldDatabase
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Shared base for Room DAO tests. Builds a fresh in-memory database per test
 * (allowMainThreadQueries is fine here since Robolectric runs tests synchronously
 * and our DAOs are suspend functions called from runTest, not real background threads).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
abstract class RoomDbTestBase {
    protected lateinit var db: CyberShieldDatabase

    @Before
    fun setUpDatabase() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    CyberShieldDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDownDatabase() {
        db.close()
    }
}
