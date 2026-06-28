package com.example.cybershield.core.domain.model

data class Module(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String? = null,
    val videoUrl: String = "",
    val category: String = "",
    val durationMins : Long = 5,
    val order: Int = 0,
    val xpReward: Int = 0,
    val quizId: String = "",
    val new : Boolean = false,
    val published: Boolean = true,
) {
    val formattedDuration: String
        get() = "$durationMins"

}