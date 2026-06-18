package com.example.cybershield.core.domain.model

data class CyberModule(
    val id:           String,
    val title:        String,
    val description:  String,
    val videoUrl:     String,
    val thumbnailUrl: String?  = null,
    val quizId:       String,         // links to its quiz
    val xpReward:     Int     = 100,
    val durationMins: Int     = 5,
    val category:     String  = "General",
    val order:        Int     = 0,     // display order
    val isNew:        Boolean = false, // highlights new modules
)