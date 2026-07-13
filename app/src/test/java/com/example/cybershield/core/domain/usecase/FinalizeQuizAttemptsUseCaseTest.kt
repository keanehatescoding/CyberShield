package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.ReadyToFinalizeAttempt
import com.example.cybershield.core.domain.repository.QuizFinalizeResult
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

    private fun stubFinalize(
        resultId: String,
        passed: Boolean,
        score: Int,
        correctCount: Int,
        percentage: Int,
    ) {
        coEvery { quizRepository.finalizeQuizAttemptServer(resultId) } returns
                Result.Success(
                    QuizFinalizeResult(
                        passed = passed,
                        score = score,
                        correctCount = correctCount,
                        percentage = percentage
                    )
                )
    }

    @Before
    fun setUp() {
        quizRepository = mockk(relaxed = true)
        awardXp = mockk()
        userRepository = mockk(relaxed = true)
        useCase = FinalizeQuizAttemptsUseCase(quizRepository, awardXp, userRepository)
    }

    @Test
    fun `does nothing when no attempts are ready`() =
        runTest {
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns emptyList()

            useCase()

            coVerify(exactly = 0) { quizRepository.finalizeQuizAttemptServer(any()) }
            coVerify(exactly = 0) { awardXp(any(), any(), any()) }
            coVerify(exactly = 0) {
                quizRepository.finalizeAttempt(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun `asks server to issue cert+badge for a passing attempt, then awards XP and finalizes`() =
        runTest {
            val attempt = readyAttempt(correctCount = 4, totalQuestions = 4) // 100%
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            stubFinalize("result-1", passed = true, score = 400, correctCount = 4, percentage = 100)
            coEvery { awardXp("user1", 4, 4) } returns Result.Success(40)

            useCase()

            // The certificate + CyberDefender badge are now issued by the server,
            // never locally — verify nothing on the client writes them.
            coVerify(exactly = 0) { userRepository.awardBadge(any(), any()) }
            coVerify(exactly = 0) { userRepository.saveCertificate(any()) }
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
    fun `does not award XP or finalize for a failing attempt, but still asks server to finalize`() =
        runTest {
            val attempt = readyAttempt(correctCount = 1, totalQuestions = 4, score = 100) // 25%
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            stubFinalize("result-1", passed = false, score = 100, correctCount = 1, percentage = 25)
            coEvery { awardXp("user1", 1, 4) } returns Result.Success(10)

            useCase()

            coVerify(exactly = 0) { userRepository.awardBadge(any(), any()) }
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
    fun `leaves attempt provisional when server finalize fails, instead of awarding XP`() =
        runTest {
            val attempt = readyAttempt(correctCount = 4, totalQuestions = 4, score = 400)
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            coEvery { quizRepository.finalizeQuizAttemptServer("result-1") } returns Result.Error(
                RuntimeException("finalize failed")
            )

            useCase()

            // A failed server finalize must NOT award XP or finalize — leave the
            // attempt provisional so it is retried next pass.
            coVerify(exactly = 0) { awardXp(any(), any(), any()) }
            coVerify(exactly = 0) {
                quizRepository.finalizeAttempt(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
            coVerify(exactly = 0) { userRepository.markQuizCompleted(any(), any()) }
        }

    @Test
    fun `leaves attempt provisional for retry when awardXp fails, instead of finalizing with zero xp`() =
        runTest {
            val attempt = readyAttempt(correctCount = 1, totalQuestions = 4, score = 100)
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            stubFinalize("result-1", passed = false, score = 100, correctCount = 1, percentage = 25)
            coEvery {
                awardXp(
                    any(),
                    any(),
                    any()
                )
            } returns Result.Error(RuntimeException("write failed"))

            useCase()

            // A failed awardXp must NOT finalize the attempt — finalizeAttempt flips
            // provisional to false and this attempt is never revisited. Finalizing here
            // with a fabricated xpEarned = 0 would permanently and silently under-reward
            // the user. It should simply stay provisional for the next sync pass.
            coVerify(exactly = 0) {
                quizRepository.finalizeAttempt(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
            coVerify(exactly = 0) { userRepository.markQuizCompleted(any(), any()) }
        }

    @Test
    fun `one attempt failing does not prevent the rest of the batch from finalizing`() =
        runTest {
            val broken = readyAttempt(
                resultId = "result-broken",
                userId = "user1",
                correctCount = 1,
                totalQuestions = 4,
                score = 100
            )
            val healthy = readyAttempt(
                resultId = "result-healthy",
                userId = "user2",
                correctCount = 4,
                totalQuestions = 4
            )
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(broken, healthy)
            coEvery { quizRepository.finalizeQuizAttemptServer("result-broken") } returns Result.Error(
                RuntimeException("finalize failed")
            )
            stubFinalize(
                "result-healthy",
                passed = true,
                score = 400,
                correctCount = 4,
                percentage = 100
            )
            coEvery { awardXp("user2", 4, 4) } returns Result.Success(40)

            useCase()

            coVerify(exactly = 0) {
                quizRepository.finalizeAttempt(
                    resultId = "result-broken",
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
            coVerify(exactly = 1) {
                quizRepository.finalizeAttempt(
                    resultId = "result-healthy",
                    any(),
                    any(),
                    any(),
                    xpEarned = 40,
                    passed = true
                )
            }
        }

    @Test
    fun `processes multiple ready attempts independently`() =
        runTest {
            val passing =
                readyAttempt(resultId = "result-pass", correctCount = 4, totalQuestions = 4)
            val failing = readyAttempt(
                resultId = "result-fail",
                userId = "user2",
                correctCount = 0,
                totalQuestions = 4,
                score = 0
            )
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(passing, failing)
            stubFinalize(
                "result-pass",
                passed = true,
                score = 400,
                correctCount = 4,
                percentage = 100
            )
            stubFinalize("result-fail", passed = false, score = 0, correctCount = 0, percentage = 0)
            coEvery { awardXp(any(), any(), any()) } returns Result.Success(0)

            useCase()

            coVerify(exactly = 1) {
                quizRepository.finalizeAttempt(
                    resultId = "result-pass",
                    any(),
                    any(),
                    any(),
                    any(),
                    passed = true
                )
            }
            coVerify(exactly = 1) {
                quizRepository.finalizeAttempt(
                    resultId = "result-fail",
                    any(),
                    any(),
                    any(),
                    any(),
                    passed = false
                )
            }
            // Neither attempt's badge/certificate is written by the client.
            coVerify(exactly = 0) { userRepository.awardBadge(any(), any()) }
            coVerify(exactly = 0) { userRepository.saveCertificate(any()) }
        }
}
