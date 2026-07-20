package com.example.cybershield.core.data.repository

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.repository.CertificateRepository
import com.example.cybershield.core.domain.util.Result
import com.example.cybershield.core.domain.util.resultOf
import com.example.cybershield.core.firebase.FirestoreCertificateDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CertificateRepositoryImpl
    @Inject
    constructor(
        private val certificateDataSource: FirestoreCertificateDataSource,
    ) : CertificateRepository {
        override suspend fun getCertificatesForUser(uid: String): Result<List<Certificate>> =
            resultOf {
                val docs = certificateDataSource.getCertificatesForUser(uid)
                val certs =
                    docs.mapNotNull { data ->
                        val id = data["_docId"] as? String ?: return@mapNotNull null
                        Certificate(
                            id = id,
                            userId = uid,
                            userName = data["userName"] as? String ?: "",
                            moduleId = data["moduleId"] as? String ?: "",
                            moduleName = data["moduleName"] as? String ?: data["quizTitle"] as? String ?: "",
                            // `as? Number` (not `as? Long`) — Firestore's Map<String, Any?>
                            // representation isn't guaranteed to hand back a Long for every
                            // numeric field. Same pitfall as LeaderboardRepositoryImpl's xp
                            // cast: a bare `as? Long` silently returns null (and therefore
                            // the `?: 0` fallback) for anything else, which would render
                            // every certificate's score as 0.
                            score = (data["score"] as? Number)?.toInt() ?: 0,
                            issuedAt =
                                (data["issuedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                                    ?: System.currentTimeMillis(),
                        )
                    }
                Result.Success(certs)
            }
    }
