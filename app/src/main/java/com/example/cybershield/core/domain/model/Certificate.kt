package com.example.cybershield.core.domain.model

import java.util.Date

data class Certificate(
    val id:         String,
    val userId:     String,
    val userName:   String,
    val moduleId:   String,
    val moduleName: String,
    val quizTitle:  String  = moduleName,   // display name for the cert
    val score:      Int     = 0,
    val issuedAt:   Long    = System.currentTimeMillis(),
) {
    // Convenience property for the UI which expects java.util.Date
    val datePassed: Date get() = Date(issuedAt)
}