package com.example.cybershield.core.domain.usecase

import com.example.cybershield.core.domain.repository.AuthRepository.AuthSession
import com.example.cybershield.core.domain.repository.UserRepository
import com.example.cybershield.core.domain.util.Result
import javax.inject.Inject

/**
 * Outcome of attempting to repair a missing user profile, as decided by
 * [EnsureUserProfileUseCase]. The ViewModel only needs to react to these —
 * it never inspects the raw error message itself.
 */
sealed class ProfileRepairOutcome {
    /** The error wasn't a missing-profile case (or there's no signed-in session) — pass it through as-is. */
    data class NotApplicable(val message: String?) : ProfileRepairOutcome()

    /** Missing-profile repair was attempted and succeeded. The live profile flow
     *  is expected to re-emit Success once Firestore reflects the new doc. */
    object RepairSucceeded : ProfileRepairOutcome()

    /** Missing-profile repair was attempted and failed. */
    object RepairFailed : ProfileRepairOutcome()

    /** A repair was already attempted earlier in this use case's lifecycle and
     *  hasn't been reset by a subsequent successful load. Matches the original
     *  ViewModel's behavior: don't retry the repair, but keep surfacing the
     *  underlying error message so the UI doesn't silently go stale. */
    data class AlreadyAttempted(val message: String?) : ProfileRepairOutcome()
}

/**
 * Decides whether a [Result.Error] from [UserRepository.getUserProfile] represents
 * a missing Firestore profile that should be auto-repaired, and performs that
 * repair at most once per instance lifecycle.
 *
 * Interface exists so ViewModels can depend on this rather than the concrete
 * implementation, making it fakeable in tests (see FakeEnsureUserProfileUseCase
 * in HomeViewModelTest) without needing a real UserRepository wired through.
 */
interface EnsureUserProfileUseCase {
    /** Call when [UserRepository.getUserProfile] emits a [Result.Success] to allow a future repair attempt again. */
    fun onProfileLoadedSuccessfully()

    suspend operator fun invoke(error: Exception, session: AuthSession?): ProfileRepairOutcome
}

/**
 * Scoped @ViewModelScoped (see [com.example.cybershield.core.data.di.UseCaseModule]) so
 * the "already attempted" flag lives exactly as long as it did when it was a
 * private field on HomeViewModel — tied to that ViewModel's lifecycle, not global.
 */
class EnsureUserProfileUseCaseImpl @Inject constructor(
    private val userRepository: UserRepository,
) : EnsureUserProfileUseCase {
    private var repairAttempted = false

    override fun onProfileLoadedSuccessfully() {
        repairAttempted = false
    }

    override suspend operator fun invoke(
        error: Exception,
        session: AuthSession?,
    ): ProfileRepairOutcome {
        val isMissingProfile = error.message?.contains("not found") == true

        if (session == null || !isMissingProfile) {
            return ProfileRepairOutcome.NotApplicable(error.message)
        }

        if (repairAttempted) {
            return ProfileRepairOutcome.AlreadyAttempted(error.message)
        }
        repairAttempted = true

        val repairResult = userRepository.createUserProfileIfNotExists(
            uid = session.uid,
            displayName = "CyberShield User",
            email = session.email ?: "",
            photoUrl = null,
        )

        return if (repairResult is Result.Error) {
            ProfileRepairOutcome.RepairFailed
        } else {
            ProfileRepairOutcome.RepairSucceeded
        }
    }
}