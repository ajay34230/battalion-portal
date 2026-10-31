package com.vision.assistant.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vision.assistant.VisionApp
import com.vision.assistant.ai.Lang
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/** On-device "seeing": ML Kit text reading (Latin + Devanagari) and object labels, a multimodal model when installed, and PDF reading. */
class SeeTool(private val app: VisionApp) {
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }

    /** Decodes a (possibly huge) image down to ~1600 px and saves a JPEG copy the multimodal model can read. */
    suspend fun prepare(uri: Uri): Pair<Bitmap, File> = withContext(Dispatchers.IO) {
        val cr = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Could not read the image.")
        val f = File(app.cacheDir, "see-${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp to f
    }

    suspend fun readText(bmp: Bitmap): String {
        val img = InputImage.fromBitmap(bmp, 0)
        val a = try { latin.process(img).await().text } catch (_: Throwable) { "" }
        val b = try { devanagari.process(img).await().text } catch (_: Throwable) { "" }
        // Keep whichever recogniser found more; they overlap on Latin text.
        return (if (b.length > a.length) b else a).trim()
    }

    suspend fun labels(bmp: Bitmap): List<String> = try {
        labeler.process(InputImage.fromBitmap(bmp, 0)).await().filter { it.confidence >= 0.6f }.take(6).map { it.text }
    } catch (_: Throwable) { emptyList() }

    suspend fun describeImage(uri: Uri, question: String, lang: Lang): String {
        val (bmp, file) = prepare(uri)
        try {
            app.engines.vision()?.describeImage(file.absolutePath, question, lang)?.let { return it }
            val text = readText(bmp); val labels = labels(bmp)
            val material = buildString {
                append("[1] Photo analysed on the phone.\n")
                if (labels.isNotEmpty()) append("Objects detected: ${labels.joinToString(", ")}.\n")
                append(if (text.isNotBlank()) "Text found in the photo:\n${text.take(3000)}" else "No readable text found.")
            }
            val q = question.ifBlank { "What is in this photo?" }
            val ans = app.engines.current().summarize(q, material, lang)
            return ans ?: buildString {
                append("I can't look at photos in detail without an image model (download Gemma 4 on the Models screen). From on-device analysis: ")
                if (labels.isNotEmpty()) append("it looks like ${labels.joinToString(", ")}. ")
                append(if (text.isNotBlank()) "Text I can read:\n${text.take(1200)}" else "I couldn't read any text.")
            }
        } finally { file.delete() }
    }

    /** Renders up to [maxPages] PDF pages, reads them with OCR and answers [question] about the document. */
    suspend fun readPdf(uri: Uri, question: String, lang: Lang, maxPages: Int = 6): String = withContext(Dispatchers.IO) {
        val pfd = app.contentResolver.openFileDescriptor(uri, "r") ?: error("Could not open the PDF.")
        val pages = StringBuilder(); var total = 0
        pfd.use { fd ->
            PdfRenderer(fd).use { r ->
                total = r.pageCount
                for (i in 0 until minOf(total, maxPages)) {
                    r.openPage(i).use { p ->
                        val w = 1400; val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val t = readText(bmp)
                        if (t.isNotBlank()) pages.append("(page ${i + 1}) ").append(t).append('\n')
                        bmp.recycle()
                    }
                }
            }
        }
        if (pages.isBlank()) return@withContext "I couldn't read any text in that PDF (it may be empty or very low quality)."
        val note = if (total > maxPages) " (I read the first $maxPages of $total pages.)" else ""
        val q = question.ifBlank { "Summarise this document." }
        val ans = app.engines.current().summarize(q, "[1] PDF document\n${pages.toString().take(6500)}", lang)
        (ans ?: ("Here is the text I could read:\n" + pages.toString().take(1500))) + note
    }
}
