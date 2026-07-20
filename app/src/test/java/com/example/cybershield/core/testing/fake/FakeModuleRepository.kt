package com.example.cybershield.core.testing.fake

import androidx.paging.PagingData
import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Test double for ModuleRepository. Configure emission sequences per-call
 * via [getModulesFlowProvider] / [getModuleByIdFlowProvider], or use the
 * simple success/error setters for the common cases.
 */
class FakeModuleRepository : ModuleRepository {
    // ── Configurable behavior ────────────────────────────────────────
    var getModulesFlowProvider: () -> Flow<Result<List<Module>>> = {
        flow { emit(Result.Success(emptyList())) }
    }

    var getModuleByIdFlowProvider: (String) -> Flow<Result<Module>> = { _ ->
        flow { emit(Result.Error(IllegalStateException("not configured"))) }
    }

    var refreshModulesResult: Result<Unit> = Result.Success(Unit)

    var pendingModulesPagingProvider: (List<String>) -> Flow<PagingData<Module>> = {
        flowOf(PagingData.empty())
    }

    var completedModulesPagingProvider: (List<String>) -> Flow<PagingData<Module>> = {
        flowOf(PagingData.empty())
    }

    private val playbackPositions = mutableMapOf<Pair<String, String>, Long>()

    // ── Call tracking ────────────────────────────────────────────────
    var savePlaybackPositionCalls = mutableListOf<Triple<String, String, Long>>()
    var refreshModulesCallCount = 0

    override fun getModules(): Flow<Result<List<Module>>> = getModulesFlowProvider()

    override fun getModuleById(moduleId: String): Flow<Result<Module>> = getModuleByIdFlowProvider(moduleId)

    override fun getPendingModulesPaged(completedIds: List<String>): Flow<PagingData<Module>> = pendingModulesPagingProvider(completedIds)

    override fun getCompletedModulesPaged(completedIds: List<String>): Flow<PagingData<Module>> =
        completedModulesPagingProvider(completedIds)

    override suspend fun refreshModules(): Result<Unit> {
        refreshModulesCallCount++
        return refreshModulesResult
    }

    override suspend fun savePlaybackPosition(
        moduleId: String,
        uid: String,
        positionMs: Long,
    ) {
        savePlaybackPositionCalls.add(Triple(moduleId, uid, positionMs))
        playbackPositions[moduleId to uid] = positionMs
    }

    override suspend fun getPlaybackPosition(
        moduleId: String,
        uid: String,
    ): Long = playbackPositions[moduleId to uid] ?: 0L

    /** Convenience: pre-seed a saved position without going through savePlaybackPosition. */
    fun seedPlaybackPosition(
        moduleId: String,
        uid: String,
        positionMs: Long,
    ) {
        playbackPositions[moduleId to uid] = positionMs
    }
}
