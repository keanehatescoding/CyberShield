package com.example.cybershield.core.data.repository

import com.example.cybershield.core.database.dao.ModuleDao
import com.example.cybershield.core.database.dao.PlaybackPositionDao
import com.example.cybershield.core.database.entity.ModuleEntity
import com.example.cybershield.core.database.entity.PlaybackPositionEntity
import com.example.cybershield.core.domain.model.Module
import com.example.cybershield.core.domain.repository.ModuleRepository
import com.example.cybershield.core.domain.util.Result
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ModuleRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val moduleDao: ModuleDao,
    private val playbackPositionDao: PlaybackPositionDao,
) : ModuleRepository {

    override fun getModules(): Flow<Result<List<Module>>> = flow {
        emit(Result.Loading)
        try {
            // Try network first
            val snapshot = firestore
                .collection("modules")
                .orderBy("order")
                .get()
                .await()

            val modules = snapshot.toObjects(
                com.example.cybershield.core.firebase.model.ModuleDto::class.java
            ).map { it.toDomain() }

            // Cache to Room
            moduleDao.clearAll()
            moduleDao.insertAll(modules.map { ModuleEntity.fromDomain(it) })

            emit(Result.Success(modules))
        } catch (e: Exception) {
            // Fall back to Room cache
            val cached = moduleDao.getAll().map { it.toDomain() }
            if (cached.isNotEmpty()) {
                emit(Result.Success(cached))
            } else {
                emit(Result.Error(e))
            }
        }
    }

    override fun getModuleById(moduleId: String): Flow<Result<Module>> = flow {
        emit(Result.Loading)
        try {
            val snapshot = firestore
                .collection("modules")
                .document(moduleId)
                .get()
                .await()

            val module = snapshot
                .toObject(com.example.cybershield.core.firebase.model.ModuleDto::class.java)
                ?.toDomain()

            if (module != null) {
                moduleDao.insertAll(listOf(ModuleEntity.fromDomain(module)))
                emit(Result.Success(module))
            } else {
                // Fall back to cache
                val cached = moduleDao.getById(moduleId)?.toDomain()
                if (cached != null) {
                    emit(Result.Success(cached))
                } else {
                    emit(Result.Error(Exception("Module not found")))
                }
            }
        } catch (e: Exception) {
            val cached = moduleDao.getById(moduleId)?.toDomain()
            if (cached != null) {
                emit(Result.Success(cached))
            } else {
                emit(Result.Error(e))
            }
        }
    }

    override suspend fun getPlaybackPosition(moduleId: String, uid: String): Long =
        playbackPositionDao.getPosition(moduleId, uid) ?: 0L

    override suspend fun savePlaybackPosition(moduleId: String, uid: String, positionMs: Long) {
        playbackPositionDao.upsert(
            PlaybackPositionEntity(
                moduleId = moduleId,
                uid = uid,
                positionMs = positionMs,
            )
        )
    }

    override suspend fun refreshModules(): Result<Unit> {
        TODO("Not yet implemented")
    }
}