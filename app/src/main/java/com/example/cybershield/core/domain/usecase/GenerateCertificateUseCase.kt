package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import java.util.UUID
import javax.inject.Inject

/**
 * Generates a certificate for a completed module and persists it
 * via UserRepository, which handles both Firestore and Storage upload.
 */
class GenerateCertificateUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(
        userId:      String,
        userName:    String,
        moduleId:    String,
        moduleName:  String,
        quizTitle: String = moduleName,
        score: Int = 0
    ): Result<Certificate> =
        try {
            val certificate = Certificate(
                id          = UUID.randomUUID().toString(),
                userId      = userId,
                userName    = userName,
                moduleId    = moduleId,
                moduleName  = moduleName,
                issuedAt    = System.currentTimeMillis(),
                quizTitle = quizTitle,
                score = score,
                pdfUrl = "https://storage.googleapis.com/cybershield-strathmore.firebasestorage.app/certificates/"
            )

            userRepository.saveCertificate(certificate)

            Result.Success(certificate)
        } catch (e: Exception) {
            Result.Error(e)
        }
}