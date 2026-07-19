package com.example.cybershield.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CertificateFormattingTest {

    @Test
    fun `formatDate returns empty string when date is null`() {
        assertEquals("", CertificateFormatting.formatDate(null))
    }

    @Test
    fun `formatDate formats a non-null date as dd MMMM yyyy`() {
        val calendar =
            java.util.Calendar.getInstance(TimeZone.getDefault()).apply {
                clear()
                set(2026, java.util.Calendar.JULY, 4)
            }
        val date = calendar.time

        val expected = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(date)

        assertEquals(expected, CertificateFormatting.formatDate(date))
    }

    @Test
    fun `cacheFileName embeds the certificate id and pdf extension`() {
        assertEquals("certificate_abc-123.pdf", CertificateFormatting.cacheFileName("abc-123"))
    }

    @Test
    fun `cacheFileName handles empty certId`() {
        assertEquals("certificate_.pdf", CertificateFormatting.cacheFileName(""))
    }

    @Test
    fun `downloadsFileName embeds the certificate id with CyberShield prefix`() {
        assertEquals("CyberShield_abc-123.pdf", CertificateFormatting.downloadsFileName("abc-123"))
    }

    @Test
    fun `cacheFileName strips path separators from a malicious certId`() {
        assertEquals(
            "certificate_______evil.pdf",
            CertificateFormatting.cacheFileName("../../evil"),
        )
    }

    @Test
    fun `downloadsFileName strips path separators from a malicious certId`() {
        assertEquals(
            "CyberShield_______evil.pdf",
            CertificateFormatting.downloadsFileName("../../evil"),
        )
    }

    @Test
    fun `sanitization leaves normal UUID-style ids untouched`() {
        val uuid = "8f14e45f-ceea-467e-bd23-11c8c8f1beac"
        assertEquals("certificate_$uuid.pdf", CertificateFormatting.cacheFileName(uuid))
    }

    @Test
    fun `scoreLabel formats score with prefix`() {
        assertEquals("Score: 85", CertificateFormatting.scoreLabel(85))
    }

    @Test
    fun `scoreLabel handles zero and negative scores without special-casing`() {
        assertEquals("Score: 0", CertificateFormatting.scoreLabel(0))
        assertEquals("Score: -1", CertificateFormatting.scoreLabel(-1))
    }

    @Test
    fun `certificateIdLabel formats id with prefix`() {
        assertEquals("Certificate ID: abc-123", CertificateFormatting.certificateIdLabel("abc-123"))
    }
}
