package com.vision.assistant.core

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class InstallState { NOT_INSTALLED, DOWNLOADED_UNVERIFIED, INSTALLED }

sealed interface DownloadResult {
    data class Installed(val file: File) : DownloadResult
    data class Unverified(val file: File, val computedSha256: String, val reason: String) : DownloadResult
    data class Failed(val reason: String) : DownloadResult
}

class ModelManager(private val dir: File, private val profile: DeviceProfile) {
    init { dir.mkdirs() }

    fun file(e: ResourceEntry) = File(dir, e.fileName)
    private fun part(e: ResourceEntry) = File(dir, e.fileName + ".part")
    private fun marker(e: ResourceEntry) = File(dir, e.fileName + ".verified")
    private fun markerValue(e: ResourceEntry, f: File) = "${e.sha256}|${f.length()}|${f.lastModified()}"

    /** INSTALLED only when the file's SHA-256 matched the manifest. The result is cached in a marker file (hashing 2 GB is slow). */
    fun state(e: ResourceEntry): InstallState {
        val f = file(e)
        if (!f.exists()) return InstallState.NOT_INSTALLED
        if (e.sha256.isBlank()) return InstallState.DOWNLOADED_UNVERIFIED
        val m = marker(e)
        if (m.exists() && m.readText() == markerValue(e, f)) return InstallState.INSTALLED
        return if (Sha256.of(f).equals(e.sha256, ignoreCase = true)) { m.writeText(markerValue(e, f)); InstallState.INSTALLED } else InstallState.DOWNLOADED_UNVERIFIED
    }

    fun partBytes(e: ResourceEntry): Long = part(e).let { if (it.exists()) it.length() else 0L }
    fun discardPart(e: ResourceEntry) { part(e).delete() }

    fun compatible(e: ResourceEntry): Boolean =
        if (e.experimental) e.kind == ResourceKind.LLM && profile.is64Bit && e.sizeMb + 1_024 <= profile.freeStorageMb else
        profile.totalRamMb >= e.minRamMb &&
            (e.kind != ResourceKind.LLM || profile.is64Bit) &&
            TierPolicy.modelFits(e.sizeMb, profile.totalRamMb, profile.freeStorageMb)

    fun delete(e: ResourceEntry) {
        file(e).delete(); part(e).delete(); marker(e).delete(); File(dir, "unzipped/${e.id}").deleteRecursively()
    }

    /** Folder holding an unzipped Vosk model, or null when not installed+verified. */
    fun voskDir(id: String): File? {
        val e = ResourceManifest.byId(id) ?: return null
        if (state(e) != InstallState.INSTALLED) return null
        return File(dir, "unzipped/$id").listFiles()?.firstOrNull { it.isDirectory }
    }

    /** Resumable download. The caller must have shown the size and obtained the owner's approval. */
    suspend fun download(e: ResourceEntry, onProgress: (Long, Long) -> Unit, onPhase: (DlPhase) -> Unit = {}): DownloadResult = withContext(Dispatchers.IO) {
        if (!compatible(e)) return@withContext DownloadResult.Failed("Not compatible with this device (RAM, storage or 32-bit CPU).")
        val p = part(e)
        try {
            // Slow or dropped connections are normal on phones: keep resuming from the .part file instead of failing.
            var attempt = 0; var lastSize = if (p.exists()) p.length() else -1L
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val err = try { transfer(e, p, onProgress) } catch (ce: kotlinx.coroutines.CancellationException) { throw ce } catch (io: java.io.IOException) { io.message ?: io.javaClass.simpleName }
                if (err == null) break
                if (err.startsWith("HTTP 4") && err != "HTTP 408" && err != "HTTP 429") return@withContext DownloadResult.Failed(err)
                val size = if (p.exists()) p.length() else 0L
                if (size > lastSize) { attempt = 0; lastSize = size } else attempt++
                if (attempt >= 8) return@withContext DownloadResult.Failed("Network kept dropping ($err). Your progress is saved; tap Resume to continue.")
                onPhase(DlPhase.QUEUED)
                kotlinx.coroutines.delay(minOf(2_000L shl attempt.coerceAtMost(4), 30_000L))
            }
            val t = file(e)
            t.delete()
            if (!p.renameTo(t)) return@withContext DownloadResult.Failed("Could not move file into place.")
            onPhase(DlPhase.VERIFYING)
            val actual = Sha256.of(t)
            when {
                e.sha256.isBlank() -> DownloadResult.Unverified(t, actual, "No official hash in the manifest.")
                actual.equals(e.sha256, ignoreCase = true) -> {
                    marker(e).writeText(markerValue(e, t))
                    if (e.fileName.endsWith(".zip")) { onPhase(DlPhase.UNPACKING); unzip(e, t) }
                    DownloadResult.Installed(t)
                }
                else -> { t.delete(); DownloadResult.Failed("Checksum mismatch; file deleted.") }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce // pause/cancel: keep the .part file, let the worker decide
        } catch (ex: Exception) {
            DownloadResult.Failed(ex.message ?: ex.javaClass.simpleName)
        }
    }

    /** One connection attempt, resuming from the .part file. Returns null on success or an error text. Throws IOException on timeouts/drops. */
    private suspend fun transfer(e: ResourceEntry, p: File, onProgress: (Long, Long) -> Unit): String? {
        val existing = if (p.exists()) p.length() else 0L
        val conn = (URL(e.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000; readTimeout = 30_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "VISION-Android/0.1")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            if (code == 416 && existing > 0) return null // already complete
            if (code != 200 && code != 206) return "HTTP $code"
            val resumed = code == 206
            val total = if (e.sizeBytes > 0) e.sizeBytes else -1L
            RandomAccessFile(p, "rw").use { raf ->
                if (resumed) raf.seek(existing) else raf.setLength(0)
                var done = if (resumed) existing else 0L
                conn.inputStream.use { input ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
                if (total > 0 && done < total) return "Connection closed early"
            }
            return null
        } finally { conn.disconnect() }
    }

    private fun unzip(e: ResourceEntry, zip: File) {
        val out = File(dir, "unzipped/${e.id}").apply { deleteRecursively(); mkdirs() }
        val root = out.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val target = File(out, entry.name)
                if (!target.canonicalPath.startsWith(root)) continue // zip-slip guard
                if (entry.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs(); target.outputStream().use { zin.copyTo(it) } }
            }
        }
    }
}
