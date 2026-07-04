package com.example.cybershield.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.cybershield.core.database.entity.ModuleEntity

@Dao
interface ModuleDao {
    @Query("SELECT * FROM modules WHERE published = 1 ORDER BY `order` ASC")
    suspend fun getAll(): List<ModuleEntity>

    @Query("SELECT * FROM modules WHERE id = :moduleId AND published = 1 LIMIT 1")
    suspend fun getById(moduleId: String): ModuleEntity?

    @Query("DELETE FROM modules")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(modules: List<ModuleEntity>)

    @Transaction
    suspend fun replaceAll(modules: List<ModuleEntity>) {
        clearAll()
        insertAll(modules)
    }

    // ── Paging3 sources — Room re-queries these automatically whenever the
    // underlying table changes, so the paged UI stays live without a manual
    // refresh path. `completedIds` is empty-safe: Room turns an empty list
    // into `NOT IN ()` / `IN ()`, which SQLite treats as "match all" /
    // "match none" respectively — exactly what we want.
    @Query(
        "SELECT * FROM modules WHERE published = 1 AND id NOT IN (:completedIds) ORDER BY `order` ASC",
    )
    fun pendingModulesPagingSource(completedIds: List<String>): PagingSource<Int, ModuleEntity>

    @Query(
        "SELECT * FROM modules WHERE published = 1 AND id IN (:completedIds) ORDER BY `order` ASC",
    )
    fun completedModulesPagingSource(completedIds: List<String>): PagingSource<Int, ModuleEntity>
}
