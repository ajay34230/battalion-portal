package com.vision.assistant.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vision.assistant.VisionApp
import com.vision.assistant.ai.LiteRtLmEngine
import com.vision.assistant.data.MemoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Runs the offline checks on this phone and records an honest status for each. A check that needs a microphone, a camera, Termux or a
 * model that is not installed is reported BLOCKED or NOT_TESTED, never PASSED. The chat and memory checks touch no internet code path,
 * but they are only evidence of offline operation if the phone really had no internet while they ran (see [OfflineSummary.headline]).
 */
object OfflineRunner {
    private suspend fun timed(id: String, title: String, block: suspend () -> Pair<CheckStatus, String>): CheckResult {
        val t0 = System.currentTimeMillis()
        val (s, d) = try { block() } catch (e: Throwable) { CheckStatus.FAILED to (e.message ?: e.javaClass.simpleName) }
        return CheckResult(id, title, s, d, System.currentTimeMillis(), System.currentTimeMillis() - t0)
    }

    suspend fun run(app: VisionApp): Pair<List<CheckResult>, Boolean> = withContext(Dispatchers.Default) {
        val before = NetworkMonitor.isOnline(app)
        val out = mutableListOf<CheckResult>()
        val mgr = Downloads.manager(app)
        val llms = ResourceManifest.entries.filter { it.kind == ResourceKind.LLM && mgr.state(it) == InstallState.INSTALLED }

        out += timed("installed", "A language model is installed") {
            if (llms.isEmpty()) CheckStatus.BLOCKED to "No model is installed. Install one in Models & downloads (needs internet once)." else CheckStatus.PASSED to llms.joinToString { it.title }
        }
        var loaded: LiteRtLmEngine? = null
        out += timed("load", "The model loads") {
            if (llms.isEmpty()) CheckStatus.BLOCKED to "Needs an installed model."
            else { val e = app.engines.current("hi"); if (e is LiteRtLmEngine) { loaded = e; CheckStatus.PASSED to "${e.name} on ${e.backend}" } else CheckStatus.FAILED to "Only the basic engine is running: ${app.engines.label.value}" }
        }
        var partials = 0
        out += timed("chat", "A conversation answers") {
            val e = loaded ?: return@timed CheckStatus.BLOCKED to "Needs a loaded model."
            val plan = withTimeoutOrNull(120_000) { e.plan("hello, how are you?") { partials++ } }
            if (plan == null) CheckStatus.FAILED to "No answer within 2 minutes." else CheckStatus.PASSED to "Answered (${plan.reply?.length ?: 0} characters)."
        }
        out += timed("stream", "Words stream while it writes") {
            if (loaded == null) CheckStatus.BLOCKED to "Needs a loaded model." else if (partials > 0) CheckStatus.PASSED to "$partials updates arrived before the end." else CheckStatus.FAILED to "The answer arrived in one piece."
        }
        out += timed("memory", "Memory saves and finds a note") {
            val dao = app.database.memory(); val tag = "selftest" + System.currentTimeMillis()
            val id = dao.insert(MemoryItem(category = "selftest", content = "$tag blue notebook"))
            val found = dao.search(tag, 3).any { it.id == id }
            dao.list().filter { it.id == id }.forEach { dao.delete(it) }
            if (found) CheckStatus.PASSED to "Saved, found and removed a test note." else CheckStatus.FAILED to "The saved note was not found."
        }
        out += timed("tasks", "Reminders and tasks are readable") { app.database.tasks().pending(); CheckStatus.PASSED to "Task list opened." }
        out += timed("ocr", "Text in an image is read (OCR)") {
            val bmp = Bitmap.createBitmap(600, 160, Bitmap.Config.ARGB_8888).also { b ->
                val c = Canvas(b); c.drawColor(Color.WHITE)
                c.drawText("VISION 2026", 30f, 100f, Paint().apply { color = Color.BLACK; textSize = 80f; isAntiAlias = true })
            }
            val text = suspendCancellableCoroutine<String?> { k ->
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { k.resume(it.text) }.addOnFailureListener { k.resume(null) }
            }
            if (text == null) CheckStatus.FAILED to "The text reader failed to start." else if (text.contains("VISION", true)) CheckStatus.PASSED to "Read \"${text.replace('\n', ' ').take(40)}\"." else CheckStatus.FAILED to "Read \"${text.take(40)}\" instead."
        }
        out += timed("vision", "A photo is understood") {
            val mm = llms.any { it.multimodal }
            if (!mm) CheckStatus.BLOCKED to "Needs an image-capable model (Gemma 4 E2B or E4B)." else CheckStatus.NOT_TESTED to "A model that can see images is installed; ask VISION about a photo to test it."
        }
        out += timed("speech", "Offline voice recognition") {
            val m = mgr.voskDir("vosk-small-en-us") != null || mgr.voskDir("vosk-small-hi") != null
            if (!m) CheckStatus.BLOCKED to "Needs an offline voice model (Models & downloads)." else CheckStatus.NOT_TESTED to "A voice model is installed; it needs the microphone, so test it in Voice setup."
        }
        out += timed("tts", "Spoken answers") { CheckStatus.NOT_TESTED to "Uses the phone's speech engine; check that an offline voice is installed in Android settings." }
        out += timed("coding", "Coding assistant runs programs") {
            if (!app.packageManager.getLaunchIntentForPackage("com.termux").let { it != null }) CheckStatus.BLOCKED to "Termux is not installed." else CheckStatus.NOT_TESTED to "Termux is installed; run a program from Coding Studio to test."
        }
        out += timed("embeddings", "Meaning-based memory search") {
            val have = ResourceManifest.byId(com.vision.assistant.memory.MemoryEmbedder.MODEL_ID)?.let { mgr.state(it) == InstallState.INSTALLED } == true
            if (!have) return@timed CheckStatus.BLOCKED to "Needs the EmbeddingGemma model (Models & downloads, 165 MB)."
            val q = com.vision.assistant.memory.MemoryEmbedder.embedNow(app, "my mother's birthday")
            val near = com.vision.assistant.memory.MemoryEmbedder.embedNow(app, "Mummy ka janamdin 12 March")
            val far = com.vision.assistant.memory.MemoryEmbedder.embedNow(app, "electricity bill is due on the 5th")
            if (q == null || near == null || far == null) CheckStatus.FAILED to "The embedding model did not load."
            else { fun dot(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }
                val dn = dot(q, near); val df = dot(q, far)
                if (dn > df) CheckStatus.PASSED to "Related text scored %.2f, unrelated %.2f.".format(dn, df) else CheckStatus.FAILED to "Related text scored %.2f but unrelated %.2f.".format(dn, df) }
        }
        val after = NetworkMonitor.isOnline(app)
        val offline = !before && !after
        OfflineStore.save(app, out, offline)
        out.toList() to offline
    }
}
