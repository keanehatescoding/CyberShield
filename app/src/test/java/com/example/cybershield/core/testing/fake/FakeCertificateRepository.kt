package com.example.cybershield.core.testing.fake


import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.repository.CertificateRepository
import com.example.cybershield.core.domain.util.Result

/**
 * Fake [CertificateRepository] for unit tests, mirroring the conventions
 * used by FakeUserRepository / FakeModuleRepository.
 */
class FakeCertificateRepository : CertificateRepository {

    private val certificatesByUid = mutableMapOf<String, List<Certificate>>()
    private val errorsByUid = mutableMapOf<String, Exception>()

    fun setCertificates(uid: String, certificates: List<Certificate>) {
        errorsByUid.remove(uid)
        certificatesByUid[uid] = certificates
    }

    fun setCertificatesError(uid: String, exception: Exception) {
        certificatesByUid.remove(uid)
        errorsByUid[uid] = exception
    }

    override suspend fun getCertificatesForUser(uid: String): Result<List<Certificate>> {
        errorsByUid[uid]?.let { return Result.Error(it) }
        return Result.Success(certificatesByUid[uid] ?: emptyList())
    }
}