package com.example.cybershield.core.firebase.model

import androidx.annotation.Keep
import com.example.cybershield.core.domain.model.Module
import com.google.firebase.firestore.DocumentId

@Keep
data class ModuleDto(
    // The doc-path id isn't part of a document's own field data, so
    // toObject()/toObjects() can't fill it in via constructor matching —
    // it's set reflectively after construction, which requires a writable
    // property. @DocumentId on a `val` (no setter) silently never gets
    // populated; the SDK's own docs specify @set:DocumentId var for exactly
    // this reason: https://firebase.google.com/docs/reference/kotlin/com/google/firebase/firestore/DocumentId
    @set:DocumentId
    var id: String = "",
    val title: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String? = null,
    val quizId: String = "",
    val xpReward: Int = 100,
    val durationMins: Long = 5,
    val category: String = "General",
    val order: Int = 0,
    val new: Boolean = false,
    val published: Boolean = true,
) {
    fun toDomain() =
        Module(
            id = id,
            title = title,
            description = description,
            videoUrl = videoUrl,
            thumbnailUrl = thumbnailUrl,
            quizId = quizId,
            xpReward = xpReward,
            durationMins = durationMins,
            category = category,
            order = order,
            new = new,
            published = published,
        )
}
