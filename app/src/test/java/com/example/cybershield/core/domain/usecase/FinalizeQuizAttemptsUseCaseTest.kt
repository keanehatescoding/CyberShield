package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.Certificate
import com.example.cybershield.core.domain.model.ReadyToFinalizeAttempt
import com.example.cybershield.core.domain.model.User
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FinalizeQuizAttemptsUseCaseTest {
    private lateinit var quizRepository: QuizRepository
    private lateinit var awardXp: AwardXpUseCase
    private lateinit var generateCertificate: GenerateCertificateUseCase
    private lateinit var userRepository: UserRepository
    private lateinit var useCase: FinalizeQuizAttemptsUseCase

    private fun readyAttempt(
        resultId: String = "result-1",
        userId: String = "user1",
        quizId: String = "quiz1",
        totalQuestions: Int = 4,
        correctCount: Int = 4,
        score: Int = 400,
    ) = ReadyToFinalizeAttempt(
        resultId = resultId,
        userId = userId,
        quizId = quizId,
        moduleId = "module1",
        moduleName = "Phishing Awareness",
        quizTitle = "Phishing Quiz",
        score = score,
        totalQuestions = totalQuestions,
        correctCount = correctCount,
    )

    @Before
    fun setUp() {
        quizRepository = mockk(relaxed = true)
        awardXp = mockk()
        generateCertificate = mockk()
        userRepository = mockk(relaxed = true)
        useCase = FinalizeQuizAttemptsUseCase(quizRepository, awardXp, generateCertificate, userRepository)
    }

    @Test
    fun `does nothing when no attempts are ready`() =
        runTest {
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns emptyList()

            useCase()

            coVerify(exactly = 0) { awardXp(any(), any(), any()) }
            coVerify(exactly = 0) { quizRepository.finalizeAttempt(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `awards XP, badge, and certificate for a passing attempt, then finalizes it`() =
        runTest {
            val attempt = readyAttempt(correctCount = 4, totalQuestions = 4) // 100%
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            coEvery { awardXp("user1", 4, 4) } returns Result.Success(40)
            coEvery { userRepository.getUserProfileOnce("user1") } returns
                Result.Success(User(uid = "user1", displayName = "Keane M.", email = "keane@example.com"))
            coEvery {
                generateCertificate(
                    userId = "user1",
                    userName = "Keane M.",
                    moduleId = "module1",
                    moduleName = "Phishing Awareness",
                    quizTitle = "Phishing Quiz",
                    score = 400,
                )
            } returns Result.Success(mockk<Certificate>(relaxed = true))

            useCase()

            coVerify { userRepository.awardBadge("user1", "CyberDefender") }
            coVerify { userRepository.markQuizCompleted("user1", "quiz1") }
            coVerify {
                quizRepository.finalizeAttempt(
                    resultId = "result-1",
                    score = 400,
                    correctCount = 4,
                    percentage = 100,
                    xpEarned = 40,
                    passed = true,
                )
            }
        }

    @Test
    fun `does not award badge or certificate for a failing attempt, but still finalizes it`() =
        runTest {
            val attempt = readyAttempt(correctCount = 1, totalQuestions = 4, score = 100) // 25%
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            coEvery { awardXp("user1", 1, 4) } returns Result.Success(10)

            useCase()

            coVerify(exactly = 0) { userRepository.awardBadge(any(), any()) }
            coVerify(exactly = 0) {
                generateCertificate(userId = any(), userName = any(), moduleId = any(), moduleName = any(), quizTitle = any(), score = any())
            }
            coVerify {
                quizRepository.finalizeAttempt(
                    resultId = "result-1",
                    score = 100,
                    correctCount = 1,
                    percentage = 25,
                    xpEarned = 10,
                    passed = false,
                )
            }
        }

    @Test
    fun `falls back to default display name when profile fetch fails`() =
        runTest {
            val attempt = readyAttempt(correctCount = 4, totalQuestions = 4)
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(40)
            coEvery { userRepository.getUserProfileOnce("user1") } returns Result.Error(RuntimeException("not found"))
            coEvery {
                generateCertificate(
                    userId = any(),
                    userName = any(),
                    moduleId = any(),
                    moduleName = any(),
                    quizTitle = any(),
                    score = any(),
                )
            } returns Result.Success(mockk<Certificate>(relaxed = true))

            useCase()

            coVerify { generateCertificate(userId = "user1", userName = "CyberShield User", moduleId = any(), moduleName = any(), quizTitle = any(), score = any()) }
        }

    @Test
    fun `leaves attempt provisional for retry when awardXp fails, instead of finalizing with zero xp`() =
        runTest {
            val attempt = readyAttempt(correctCount = 1, totalQuestions = 4, score = 100)
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            coEvery { awardXp(any(), any(), any()) } returns Result.Error(RuntimeException("write failed"))

            useCase()

            // A failed awardXp must NOT finalize the attempt — finalizeAttempt flips
            // provisional to false and this attempt is never revisited. Finalizing here
            // with a fabricated xpEarned = 0 would permanently and silently under-reward
            // the user. It should simply stay provisional for the next sync pass.
            coVerify(exactly = 0) { quizRepository.finalizeAttempt(any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { userRepository.markQuizCompleted(any(), any()) }
        }

    @Test
    fun `one attempt failing does not prevent the rest of the batch from finalizing`() =
        runTest {
            val broken = readyAttempt(resultId = "result-broken", userId = "user1", correctCount = 1, totalQuestions = 4, score = 100)
            val healthy = readyAttempt(resultId = "result-healthy", userId = "user2", correctCount = 4, totalQuestions = 4)
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(broken, healthy)
            coEvery { awardXp("user1", 1, 4) } returns Result.Error(RuntimeException("write failed"))
            coEvery { awardXp("user2", 4, 4) } returns Result.Success(40)
            coEvery { userRepository.getUserProfileOnce("user2") } returns Result.Error(RuntimeException("n/a"))
            coEvery {
                generateCertificate(userId = any(), userName = any(), moduleId = any(), moduleName = any(), quizTitle = any(), score = any())
            } returns Result.Success(mockk<Certificate>(relaxed = true))

            useCase()

            coVerify(exactly = 0) { quizRepository.finalizeAttempt(resultId = "result-broken", any(), any(), any(), any(), any()) }
            coVerify(exactly = 1) {
                quizRepository.finalizeAttempt(resultId = "result-healthy", any(), any(), any(), xpEarned = 40, passed = true)
            }
        }

    @Test
    fun `processes multiple ready attempts independently`() =
        runTest {
            val passing = readyAttempt(resultId = "result-pass", correctCount = 4, totalQuestions = 4)
            val failing = readyAttempt(resultId = "result-fail", userId = "user2", correctCount = 0, totalQuestions = 4, score = 0)
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(passing, failing)
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)
            coEvery { userRepository.getUserProfileOnce(any()) } returns Result.Error(RuntimeException("n/a"))
            coEvery {
                generateCertificate(userId = any(), userName = any(), moduleId = any(), moduleName = any(), quizTitle = any(), score = any())
            } returns Result.Success(mockk<Certificate>(relaxed = true))

            useCase()

            coVerify(exactly = 1) { quizRepository.finalizeAttempt(resultId = "result-pass", any(), any(), any(), any(), passed = true) }
            coVerify(exactly = 1) { quizRepository.finalizeAttempt(resultId = "result-fail", any(), any(), any(), any(), passed = false) }
            // Only the passing attempt gets a badge/certificate.
            coVerify(exactly = 1) { userRepository.awardBadge("user1", "CyberDefender") }
            coVerify(exactly = 0) { userRepository.awardBadge("user2", any()) }
        }
}
