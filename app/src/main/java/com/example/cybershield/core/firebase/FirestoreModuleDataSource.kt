package com.example.cybershield.core.firebase

import com.example.cybershield.core.firebase.model.ModuleDto
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirestoreModuleDataSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
    ) {
        suspend fun getAllModules(): List<ModuleDto> {
            val snapshot =
                firestore
                    .collection(COLLECTION_MODULES)
                    .orderBy(FIELD_ORDER)
                    .get()
                    .await()
            return snapshot.toObjects(ModuleDto::class.java)
        }

        suspend fun getModuleById(moduleId: String): ModuleDto? {
            val snapshot =
                firestore
                    .collection(COLLECTION_MODULES)
                    .document(moduleId)
                    .get()
                    .await()
            return snapshot.toObject(ModuleDto::class.java)
        }

        companion object {
            private const val COLLECTION_MODULES = "modules"
            private const val FIELD_ORDER = "order"
        }
    }
