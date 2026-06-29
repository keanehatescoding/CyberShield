package com.example.cybershield.core.domain.repository

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.util.Result

interface CertificateRepository {
    suspend fun getCertificatesForUser(uid: String): Result<List<Certificate>>
}
