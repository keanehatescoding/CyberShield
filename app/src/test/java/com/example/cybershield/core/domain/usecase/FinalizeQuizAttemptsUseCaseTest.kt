package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.model.ReadyToFinalizeAttempt
import com.example.cybershield.core.domain.repository.QuizFinalizeResult
import com.example.cybershield.core.domain.repository.QuizRepository
import com.example.cybershield.core.domain.util.CrashReporter
import com.example.cybershield.core.domain.util.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FinalizeQuizAttemptsUseCaseTest {
    private lateinit var quizRepository: QuizRepository
    private lateinit var crashReporter: CrashReporter
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

    // xpEarned is now part of the server's finalize response — it's computed
    // and applied atomically alongside the score/correctCount/percentage
    // recompute, not via a separate client-side awardXp call/write.
    private fun stubFinalize(
        resultId: String,
        passed: Boolean,
        score: Int,
        correctCount: Int,
        percentage: Int,
        xpEarned: Int,
    ) {
        coEvery { quizRepository.finalizeQuizAttemptServer(resultId) } returns
            Result.Success(
                QuizFinalizeResult(
                    passed = passed,
                    score = score,
                    correctCount = correctCount,
                    percentage = percentage,
                    xpEarned = xpEarned,
                ),
            )
    }

    @Before
    fun setUp() {
        quizRepository = mockk(relaxed = true)
        crashReporter = mockk(relaxed = true)
        useCase = FinalizeQuizAttemptsUseCase(quizRepository, crashReporter)
    }

    @Test
    fun `does nothing when no attempts are ready`() =
        runTest {
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns emptyList()

            useCase()

            coVerify(exactly = 0) { quizRepository.finalizeQuizAttemptServer(any()) }
            coVerify(exactly = 0) {
                quizRepository.finalizeAttempt(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun `asks server to issue cert+badge and award XP for a passing attempt, then finalizes`() =
        runTest {
            val attempt = readyAttempt(correctCount = 4, totalQuestions = 4) // 100%
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            stubFinalize("result-1", passed = true, score = 400, correctCount = 4, percentage = 100, xpEarned = 40)

            useCase()

            // The certificate + CyberDefender badge + XP are now all issued by the
            // server, never locally — verify nothing on the client writes them.
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
    fun `still awards XP and finalizes a failing attempt (no cert or badge)`() =
        runTest {
            val attempt = readyAttempt(correctCount = 1, totalQuestions = 4, score = 100) // 25%
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            stubFinalize("result-1", passed = false, score = 100, correctCount = 1, percentage = 25, xpEarned = 10)

            useCase()

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
            coEvery { quizRepository.finalizeQuizAttemptServer("result-1") } returns
                Result.Error(
                    RuntimeException("finalize failed"),
                )

            useCase()

            // A failed server finalize must NOT award XP or finalize — leave the
            // attempt provisional so it is retried next pass.
            coVerify(exactly = 0) {
                quizRepository.finalizeAttempt(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun `one attempt failing does not prevent the rest of the batch from finalizing`() =
        runTest {
            val broken =
                readyAttempt(
                    resultId = "result-broken",
                    userId = "user1",
                    correctCount = 1,
                    totalQuestions = 4,
                    score = 100,
                )
            val healthy =
                readyAttempt(
                    resultId = "result-healthy",
                    userId = "user2",
                    correctCount = 4,
                    totalQuestions = 4,
                )
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(broken, healthy)
            coEvery { quizRepository.finalizeQuizAttemptServer("result-broken") } returns
                Result.Error(
                    RuntimeException("finalize failed"),
                )
            stubFinalize(
                "result-healthy",
                passed = true,
                score = 400,
                correctCount = 4,
                percentage = 100,
                xpEarned = 40,
            )

            useCase()

            coVerify(exactly = 0) {
                quizRepository.finalizeAttempt(
                    resultId = "result-broken",
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
            coVerify(exactly = 1) {
                quizRepository.finalizeAttempt(
                    resultId = "result-healthy",
                    any(),
                    any(),
                    any(),
                    xpEarned = 40,
                    passed = true,
                )
            }
        }

    @Test
    fun `processes multiple ready attempts independently`() =
        runTest {
            val passing =
                readyAttempt(resultId = "result-pass", correctCount = 4, totalQuestions = 4)
            val failing =
                readyAttempt(
                    resultId = "result-fail",
                    userId = "user2",
                    correctCount = 0,
                    totalQuestions = 4,
                    score = 0,
                )
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(passing, failing)
            stubFinalize(
                "result-pass",
                passed = true,
                score = 400,
                correctCount = 4,
                percentage = 100,
                xpEarned = 40,
            )
            stubFinalize("result-fail", passed = false, score = 0, correctCount = 0, percentage = 0, xpEarned = 0)

            useCase()

            coVerify(exactly = 1) {
                quizRepository.finalizeAttempt(
                    resultId = "result-pass",
                    any(),
                    any(),
                    any(),
                    any(),
                    passed = true,
                )
            }
            coVerify(exactly = 1) {
                quizRepository.finalizeAttempt(
                    resultId = "result-fail",
                    any(),
                    any(),
                    any(),
                    any(),
                    passed = false,
                )
            }
        }

    @Test
    fun `records a finalize failure but does not report to crash reporter below the retry limit`() =
        runTest {
            val attempt = readyAttempt(resultId = "result-1")
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            coEvery { quizRepository.finalizeQuizAttemptServer("result-1") } returns
                Result.Error(
                    RuntimeException("finalize failed"),
                )
            coEvery { quizRepository.recordFinalizeFailure("result-1") } returns false

            useCase()

            coVerify(exactly = 1) { quizRepository.recordFinalizeFailure("result-1") }
            verify(exactly = 0) { crashReporter.recordException(any(), any()) }
        }

    @Test
    fun `reports to crash reporter once recordFinalizeFailure abandons the attempt`() =
        runTest {
            // Simulates the case this fix targets: the attempt's quizResults
            // docs were overwritten server-side by a later retake of the same
            // quiz, so finalizeQuizAttemptServer will never succeed for it
            // again. Once recordFinalizeFailure reports the retry limit was
            // hit, the use case should stop silently swallowing this and
            // surface it once instead.
            val attempt = readyAttempt(resultId = "result-stuck")
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            coEvery { quizRepository.finalizeQuizAttemptServer("result-stuck") } returns
                Result.Error(
                    RuntimeException("Attempt is incomplete: 2/4 questions graded."),
                )
            coEvery { quizRepository.recordFinalizeFailure("result-stuck") } returns true

            useCase()

            coVerify(exactly = 1) { quizRepository.recordFinalizeFailure("result-stuck") }
            verify(exactly = 1) {
                crashReporter.recordException(any(), mapOf("resultId" to "result-stuck"))
            }
            coVerify(exactly = 0) {
                quizRepository.finalizeAttempt(any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `records swallowed exception to crash reporter with the resultId`() =
        runTest {
            val attempt = readyAttempt(resultId = "result-1")
            coEvery { quizRepository.getAttemptsReadyToFinalize() } returns listOf(attempt)
            stubFinalize("result-1", passed = true, score = 400, correctCount = 4, percentage = 100, xpEarned = 40)
            val boom = RuntimeException("finalizeAttempt blew up")
            coEvery {
                quizRepository.finalizeAttempt(
                    resultId = "result-1",
                    score = 400,
                    correctCount = 4,
                    percentage = 100,
                    xpEarned = 40,
                    passed = true,
                )
            } throws boom

            useCase()

            verify(exactly = 1) {
                crashReporter.recordException(boom, mapOf("resultId" to "result-1"))
            }
        }
}
