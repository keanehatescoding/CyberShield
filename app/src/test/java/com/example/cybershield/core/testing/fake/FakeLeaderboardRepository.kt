package com.example.cybershield.core.testing.fake

import com.example.cybershield.core.domain.model.LeaderboardEntry
import com.example.cybershield.core.domain.repository.LeaderboardRepository
import com.example.cybershield.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake [LeaderboardRepository] for unit tests, mirroring the conventions
 * used by FakeModuleRepository / FakeCertificateRepository.
 */
class FakeLeaderboardRepository : LeaderboardRepository {
    var getTopLeaderboardFlowProvider: (Int) -> Flow<Result<List<LeaderboardEntry>>> = {
        flow { emit(Result.Success(emptyList())) }
    }

    var getTopLeaderboardCalls = mutableListOf<Int>()

    override fun getTopLeaderboard(limit: Int): Flow<Result<List<LeaderboardEntry>>> {
        getTopLeaderboardCalls.add(limit)
        return getTopLeaderboardFlowProvider(limit)
    }
}