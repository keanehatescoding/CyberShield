package com.example.cybershield.core.data.repository

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.repository.CertificateRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.resultOf
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class CertificateRepositoryImpl
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
    ) : CertificateRepository {
        override suspend fun getCertificatesForUser(uid: String): Result<List<Certificate>> =
            resultOf {
                val snap =
                    firestore
                        .collection("users")
                        .document(uid)
                        .collection("certificates")
                        .get()
                        .await()

                val certs =
                    snap.documents.mapNotNull { doc ->
                        // ★ Field names here match the canonical domain model,
                        // resolving the issuedAt/datePassed mismatch from the bug report
                        Certificate(
                            id = doc.id,
                            userId = uid,
                            userName = doc.getString("userName") ?: "",
                            moduleId = doc.getString("moduleId") ?: "",
                            moduleName = doc.getString("moduleName") ?: doc.getString("quizTitle") ?: "",
                            score = doc.getLong("score")?.toInt() ?: 0,
                            issuedAt = doc.getDate("issuedAt")?.time ?: System.currentTimeMillis(),
                        )
                    }
                Result.Success(certs)
            }
    }
