package com.example.cybershield.core.domain.model

data class User(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val xp: Int = 0,
    val level: Int = 1,
    val badges: List<String> = emptyList(),
    val completedQuizzes: List<String> = emptyList(),
    val completedModules: List<String> = emptyList(),
    val fcmToken: String? = null,
    val createdAt: Long = 0L,
    val lastSignedInAt: Long = 0L,
) {
    // Computed level from XP — 100 XP per level
    val computedLevel: Int
        get() = (xp / 100) + 1

    // XP progress within current level (0.0 to 1.0)
    val xpProgress: Float
        get() = (xp % 100) / 100f

    // XP needed to reach next level
    val xpToNextLevel: Int
        get() = 100 - (xp % 100)
}
