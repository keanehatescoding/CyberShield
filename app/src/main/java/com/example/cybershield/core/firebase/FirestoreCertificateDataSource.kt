package com.example.cybershield.core.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreCertificateDataSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
    ) {
        suspend fun getCertificatesForUser(uid: String): List<Map<String, Any?>> {
            val snap =
                firestore
                    .collection("users")
                    .document(uid)
                    .collection("certificates")
                    .get()
                    .await()
            return snap.documents.map { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["_docId"] = doc.id
                data
            }
        }
    }
