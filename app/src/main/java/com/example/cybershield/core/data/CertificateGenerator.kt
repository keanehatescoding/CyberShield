package com.example.cybershield.core.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class CertificateGenerator(
    private val context: Context,
) {
    companion object {
        const val PAGE_WIDTH = 842 // A4 landscape in points
        const val PAGE_HEIGHT = 595
    }

    /** Thrown when saveToDownloads() needs WRITE_EXTERNAL_STORAGE on API 26-28, but it isn't granted. */
    class MissingStoragePermissionException :
        Exception(
            "WRITE_EXTERNAL_STORAGE permission is required to save files on this Android version.",
        )

    /**
     * Thrown when saveToDownloads() could not actually write the file — e.g.
     * the MediaStore insert was rejected, or the resulting content Uri could
     * not be opened for writing. Without this, either failure was silently
     * swallowed: the function returned normally having written nothing, and
     * the caller (CertificateScreen) told the user "Saved to Downloads"
     * regardless.
     */
    class SaveFailedException(
        message: String,
    ) : Exception(message)

    // ── Generate PDF and save to cache ─────────────────────────────────
    suspend fun generate(
        userName: String,
        quizTitle: String,
        score: Int,
        date: Date?,
        certId: String,
    ): File =
        withContext(Dispatchers.IO) {
            val document = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = document.startPage(pageInfo)

                drawCertificate(page.canvas, userName, quizTitle, score, date, certId)

                document.finishPage(page)

                val subDir = File(context.cacheDir, "certificates").apply { mkdirs() }
                val file = File(subDir, CertificateFormatting.cacheFileName(certId))
                file.outputStream().use { document.writeTo(it) }
                file
            } finally {
                // Always released, even if drawCertificate()/writeTo() throws —
                // previously document.close() only ran on the success path, so
                // an exception mid-generation leaked the native PdfDocument.
                document.close()
            }
        }

    // ── Draw certificate content onto canvas ───────────────────────────
    private fun drawCertificate(
        canvas: Canvas,
        userName: String,
        quizTitle: String,
        score: Int,
        date: Date?,
        certId: String,
    ) {
        val w = PAGE_WIDTH.toFloat()
        val h = PAGE_HEIGHT.toFloat()

        // Background
        canvas.drawColor(Color.rgb(250, 248, 240))

        // Border
        val borderPaint =
            Paint().apply {
                color = Color.rgb(21, 101, 192)
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
        canvas.drawRect(16f, 16f, w - 16f, h - 16f, borderPaint)
        canvas.drawRect(
            24f,
            24f,
            w - 24f,
            h - 24f,
            borderPaint.apply {
                strokeWidth = 2f
                color = Color.rgb(100, 150, 220)
            },
        )

        // Title
        val titlePaint =
            Paint().apply {
                color = Color.rgb(21, 101, 192)
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText("CYBERSHIELD", w / 2, 80f, titlePaint)

        val subtitlePaint =
            Paint().apply {
                color = Color.rgb(80, 80, 80)
                textSize = 18f
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText("Certificate of Completion", w / 2, 115f, subtitlePaint)

        // Divider line
        val linePaint =
            Paint().apply {
                color = Color.rgb(21, 101, 192)
                strokeWidth = 1.5f
            }
        canvas.drawLine(80f, 130f, w - 80f, 130f, linePaint)

        // Body text
        val bodyPaint =
            Paint().apply {
                color = Color.rgb(100, 100, 100)
                textSize = 14f
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText("This certifies that", w / 2, 175f, bodyPaint)

        // Username
        val namePaint =
            Paint().apply {
                color = Color.rgb(21, 101, 192)
                textSize = 36f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText(userName, w / 2, 225f, namePaint)

        canvas.drawText("has successfully completed", w / 2, 265f, bodyPaint)

        // Quiz title
        val quizPaint =
            Paint().apply {
                color = Color.rgb(30, 30, 30)
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText(quizTitle, w / 2, 310f, quizPaint)

        // Divider
        canvas.drawLine(80f, 340f, w - 80f, 340f, linePaint)

        // Footer — score + date
        val footerPaint =
            Paint().apply {
                color = Color.rgb(80, 80, 80)
                textSize = 13f
            }
        canvas.drawText(
            CertificateFormatting.scoreLabel(score),
            100f,
            375f,
            footerPaint,
        )
        canvas.drawText(
            CertificateFormatting.formatDate(date),
            w - 100f,
            375f,
            footerPaint.apply { textAlign = Paint.Align.RIGHT },
        )

        // Certificate ID
        val idPaint =
            Paint().apply {
                color = Color.rgb(150, 150, 150)
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText(CertificateFormatting.certificateIdLabel(certId), w / 2, h - 30f, idPaint)
    }

    // ── Share via Android share sheet ──────────────────────────────────
    fun share(
        context: Context,
        file: File,
    ) {
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file,
            )
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(intent, "Share certificate"))
    }

    // ── Save to Downloads ─────────────────────────────────────────────

    /**
     * @throws MissingStoragePermissionException on API 26-28 if WRITE_EXTERNAL_STORAGE
     *   has not been granted. Callers should catch this, request the permission via
     *   the standard runtime permission flow, and retry on grant.
     */
    suspend fun saveToDownloads(
        userName: String,
        quizTitle: String,
        score: Int,
        date: Date?,
        certId: String,
    ) = withContext(Dispatchers.IO) {
        val file = generate(userName, quizTitle, score, date, certId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, CertificateFormatting.downloadsFileName(certId))
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            val uri =
                context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: throw SaveFailedException("Couldn't create a Downloads entry for the certificate.")
            try {
                val outputStream =
                    context.contentResolver.openOutputStream(uri)
                        ?: throw SaveFailedException("Couldn't open the Downloads entry to write the certificate.")
                outputStream.use { os -> file.inputStream().use { input -> input.copyTo(os) } }
            } catch (e: Exception) {
                // insert() already created a real (now empty/partial) entry —
                // clean it up on any write failure so a retry doesn't leave
                // orphaned certificate entries accumulating in Downloads.
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            // Pre-Q: writing to the public Downloads dir is a dangerous-permission
            // operation and must be checked at runtime, not just declared in the manifest.
            val granted =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                throw MissingStoragePermissionException()
            }

            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            file.copyTo(File(downloadsDir, CertificateFormatting.downloadsFileName(certId)), overwrite = true)
        }
    }
}
