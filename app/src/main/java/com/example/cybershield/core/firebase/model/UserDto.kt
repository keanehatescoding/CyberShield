package com.example.cybershield.core.firebase.model

import androidx.annotation.Keep
import com.example.cybershield.core.domain.model.User
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@Keep
data class UserDto(
    @DocumentId
    val uid:              String   = "",
    val displayName:      String   = "",
    val email:            String   = "",
    val photoUrl:         String?  = null,
    val xp:               Int      = 0,
    val level:            Int      = 1,
    val badges:           List<String> = emptyList(),
    val completedQuizzes: List<String> = emptyList(),
    val completedModules: List<String> = emptyList(),
    val fcmToken:         String?  = null,
    @ServerTimestamp
    val createdAt:        Date?    = null,
    @ServerTimestamp
    val lastSignedInAt:   Date?    = null,
) {
    // Map Firestore DTO → clean domain model
    fun toDomain(): User = User(
        uid              = uid,
        displayName      = displayName,
        email            = email,
        photoUrl         = photoUrl,
        xp               = xp,
        level            = level,
        badges           = badges,
        completedQuizzes = completedQuizzes,
        completedModules = completedModules,
        fcmToken         = fcmToken,
        createdAt        = createdAt?.time ?: 0L,
        lastSignedInAt   = lastSignedInAt?.time ?: 0L,
    )
}