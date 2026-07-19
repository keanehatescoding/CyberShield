package com.example.cybershield.core.domain.usecase.auth

import com.example.cybershield.core.database.CyberShieldDatabase
import com.example.cybershield.core.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Signs the current user out and wipes the local Room cache.
 *
 * Without the wipe, unsynced rows in `quiz_results` / `quiz_attempts`
 * (queried by SyncQuizResultsWorker via getPendingResults() /
 * getProvisionalAttempts(), neither of which filters by userId) and any
 * cached `modules` / `playback_positions` rows stay on disk after sign-out.
 * On a shared or multi-account device, the next person to sign in inherits
 * the previous user's offline quiz answers, XP, and progress the moment a
 * sync pass runs — silently misattributing them.
 *
 * clearAllTables() does mean any not-yet-synced attempt is lost if the user
 * signs out while offline; that's judged an acceptable, visible trade-off
 * against silently leaking one account's data into another's.
 */
class SignOutUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val database: CyberShieldDatabase,
    ) {
        suspend operator fun invoke() {
            authRepository.signOut()
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
        }
    }
