package com.example.cybershield.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cybershield.core.database.entity.ModuleEntity

@Dao
interface ModuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(modules: List<ModuleEntity>)

    @Query("SELECT * FROM modules WHERE isPublished = 1 ORDER BY `order` ASC")
    suspend fun getAll(): List<ModuleEntity>

    @Query("SELECT * FROM modules WHERE id = :moduleId LIMIT 1")
    suspend fun getById(moduleId: String): ModuleEntity?

    @Query("DELETE FROM modules")
    suspend fun clearAll()
}