package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.repository.AuthRepository

/**
 * Scripted fake for the extracted use case — HomeViewModelTest only needs to
 * verify delegation (call the use case, react to whatever it returns), not
 * re-derive the repair state machine, which EnsureUserProfileUseCaseTest already
 * covers directly against the real implementation.
 */
class FakeEnsureUserProfileUseCase : EnsureUserProfileUseCase {
    var outcomeToReturn: ProfileRepairOutcome = ProfileRepairOutcome.NotApplicable(null)
    var onProfileLoadedSuccessfullyCallCount = 0
    var lastSessionPassed: AuthRepository.AuthSession? = null

    override fun onProfileLoadedSuccessfully() {
        onProfileLoadedSuccessfullyCallCount++
    }

    override suspend operator fun invoke(
        error: Exception,
        session: AuthRepository.AuthSession?,
    ): ProfileRepairOutcome {
        lastSessionPassed = session
        return outcomeToReturn
    }
}
