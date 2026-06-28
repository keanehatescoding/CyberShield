package com.example.cybershield.core.domain.model

data class LeaderboardEntry(
    val uid:         String,
    val displayName: String,
    val xp:          Int,
    val level:       Int,
    val badges:      List<String>,
)