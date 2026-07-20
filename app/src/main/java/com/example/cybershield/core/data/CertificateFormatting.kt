package com.example.cybershield.core.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure-Kotlin formatting logic for certificates, pulled out of
 * [CertificateGenerator] so it can be unit tested on the plain JVM without
 * requiring android.graphics/PdfDocument (which only exist on-device or
 * under Robolectric).
 */
object CertificateFormatting {
    private const val DATE_PATTERN = "dd MMMM yyyy"

    /** Formats [date] as "dd MMMM yyyy", or "" when [date] is null. */
    fun formatDate(date: Date?): String = date?.let { SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(it) } ?: ""

    /**
     * certId is the quiz attempt's resultId, which the client itself
     * generates (see QuizViewModel.resultIdProvider) and later reuses
     * verbatim as a filesystem path segment in [cacheFileName] /
     * [downloadsFileName]. Nothing constrains its charset before it gets
     * here, so a "/" or ".." in it turns what's meant to be a single file
     * name into multiple path segments — e.g. certId "../../evil" placed
     * straight into `File(subDir, "certificate_$certId.pdf")` walks back
     * out of the intended `cacheDir/certificates` directory. Stripping
     * everything but a safe, filename-legal charset keeps the id readable
     * (existing UUIDs are untouched) while removing any path meaning.
     */
    private fun sanitizeForFileName(certId: String): String = certId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** File name used when generating the certificate into the app cache dir. */
    fun cacheFileName(certId: String): String = "certificate_${sanitizeForFileName(certId)}.pdf"

    /** File name used when saving/copying the certificate into Downloads. */
    fun downloadsFileName(certId: String): String = "CyberShield_${sanitizeForFileName(certId)}.pdf"

    /** Footer label rendered next to the score. */
    fun scoreLabel(score: Int): String = "Score: $score"

    /** Footer label rendered with the certificate's unique id. */
    fun certificateIdLabel(certId: String): String = "Certificate ID: $certId"
}
