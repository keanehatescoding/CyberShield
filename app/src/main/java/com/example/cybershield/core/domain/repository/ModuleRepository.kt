package com.example.cybershield.core.domain.repository

import androidx.paging.PagingData
import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface ModuleRepository {
    /** Real-time list of all modules — serves Room cache, refreshes from Firestore. */
    fun getModules(): Flow<Result<List<Module>>>

    /**
     * Paged modules NOT yet completed by the user, backed by Room. Re-queries
     * live as the cache changes; call [refreshModules] beforehand (or rely on
     * pull-to-refresh) to pull fresh data from Firestore into the cache.
     */
    fun getPendingModulesPaged(completedIds: List<String>): Flow<PagingData<Module>>

    /** Paged modules the user HAS completed, backed by Room. */
    fun getCompletedModulesPaged(completedIds: List<String>): Flow<PagingData<Module>>

    /** Single module by ID — serves cache first, then Firestore. */
    fun getModuleById(moduleId: String): Flow<Result<Module>>

    /** Force refresh from Firestore into Room cache. */
    suspend fun refreshModules(): Result<Unit>

    /** Save video playback position for resume-on-return. */
    suspend fun savePlaybackPosition(
        moduleId: String,
        uid: String,
        positionMs: Long,
    )

    /** Get saved playback position for a module. */
    suspend fun getPlaybackPosition(
        moduleId: String,
        uid: String,
    ): Long // returns 0L if none saved
}
