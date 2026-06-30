package com.example.cybershield.core.data.repository

import com.example.cybershield.core.database.dao.ModuleDao
import com.example.cybershield.core.database.dao.PlaybackPositionDao
import com.example.cybershield.core.database.entity.ModuleEntity
import com.example.cybershield.core.database.entity.PlaybackPositionEntity
import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.resultOf
import com.example.cybershield.core.firebase.FirestoreModuleDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModuleRepositoryImpl
    @Inject
    constructor(
        private val moduleDataSource: FirestoreModuleDataSource,
        private val moduleDao: ModuleDao,
        private val playbackPositionDao: PlaybackPositionDao,
    ) : ModuleRepository {
        private suspend fun fetchAndCacheModules(): List<Module> {
            val modules =
                moduleDataSource
                    .getAllModules()
                    .map { it.toDomain() }

            moduleDao.replaceAll(modules.map { ModuleEntity.fromDomain(it) })

            return modules
        }

        // ── Real-time-ish module list — network first, cache fallback ───────
        override fun getModules(): Flow<Result<List<Module>>> =
            flow {
                emit(Result.Loading)
                try {
                    emit(Result.Success(fetchAndCacheModules()))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val cached = moduleDao.getAll().map { it.toDomain() }
                    if (cached.isNotEmpty()) {
                        emit(Result.Success(cached))
                        emit(Result.Error(e, isStale = true))
                    } else {
                        emit(Result.Error(e))
                    }
                }
            }

        override fun getModuleById(moduleId: String): Flow<Result<Module>> =
            flow {
                emit(Result.Loading)
                try {
                    val module = moduleDataSource.getModuleById(moduleId)?.toDomain()
                    if (module != null) {
                        moduleDao.insertAll(listOf(ModuleEntity.fromDomain(module)))
                        emit(Result.Success(module))
                    } else {
                        val cached = moduleDao.getById(moduleId)?.toDomain()
                        if (cached != null) {
                            emit(Result.Success(cached))
                            emit(Result.Error(Exception("Module not found remotely"), isStale = true))
                        } else {
                            emit(Result.Error(Exception("Module not found")))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val cached = moduleDao.getById(moduleId)?.toDomain()
                    if (cached != null) {
                        emit(Result.Success(cached))
                        emit(Result.Error(e, isStale = true))
                    } else {
                        emit(Result.Error(e))
                    }
                }
            }

        override suspend fun getPlaybackPosition(
            moduleId: String,
            uid: String,
        ): Long = playbackPositionDao.getPosition(moduleId, uid) ?: 0L

        override suspend fun savePlaybackPosition(
            moduleId: String,
            uid: String,
            positionMs: Long,
        ) {
            playbackPositionDao.upsert(
                PlaybackPositionEntity(
                    moduleId = moduleId,
                    uid = uid,
                    positionMs = positionMs,
                ),
            )
        }

        // ── One-shot manual refresh — honestly reports network failure ──────
        override suspend fun refreshModules(): Result<Unit> =
            resultOf {
                fetchAndCacheModules()
                Result.Success(Unit)
            }
    }
