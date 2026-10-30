package com.vision.assistant.core

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class InstallState { NOT_INSTALLED, DOWNLOADED_UNVERIFIED, INSTALLED }

sealed interface DownloadResult {
    data class Installed(val file: File) : DownloadResult
    /** Downloaded but hash could not be confirmed: NOT usable. */
    data class Unverified(val file: File, val computedSha256: String, val reason: String) : DownloadResult
    data class Failed(val reason: String) : DownloadResult
}

class ModelManager(private val dir: File, private val profile: DeviceProfile) {
    init { dir.mkdirs() }

    private fun target(e: ResourceEntry) = File(dir, e.id + ".bin")
    private fun part(e: ResourceEntry) = File(dir, e.id + ".part")

    fun state(e: ResourceEntry): InstallState {
        val f = target(e)
        if (!f.exists()) return if (part(e).exists()) InstallState.NOT_INSTALLED else InstallState.NOT_INSTALLED
        return if (e.sha256.isNotBlank() && Sha256.of(f).equals(e.sha256, ignoreCase = true)) InstallState.INSTALLED
        else InstallState.DOWNLOADED_UNVERIFIED
    }

    fun compatible(e: ResourceEntry): Boolean =
        TierPolicy.modelFits(e.sizeMb, profile.totalRamMb, profile.freeStorageMb) && profile.totalRamMb >= e.minRamMb

    fun delete(e: ResourceEntry) { target(e).delete(); part(e).delete() }

    /** Resumable download. Caller must have obtained owner approval for sizes above 50 MB. */
    suspend fun download(e: ResourceEntry, onProgress: (Long, Long) -> Unit): DownloadResult = withContext(Dispatchers.IO) {
        if (!compatible(e)) return@withContext DownloadResult.Failed("Not compatible with this device (RAM/storage).")
        val p = part(e)
        try {
            val existing = if (p.exists()) p.length() else 0L
            val conn = (URL(e.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            val code = conn.responseCode
            if (code != 200 && code != 206) return@withContext DownloadResult.Failed("HTTP $code")
            val resumed = code == 206
            val total = conn.contentLengthLong.let { if (it > 0) it + (if (resumed) existing else 0) else -1L }
            RandomAccessFile(p, "rw").use { raf ->
                if (resumed) raf.seek(existing) else raf.setLength(0)
                var done = if (resumed) existing else 0L
                conn.inputStream.use { input ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
            val t = target(e)
            t.delete()
            if (!p.renameTo(t)) return@withContext DownloadResult.Failed("Could not move file into place.")
            val actual = Sha256.of(t)
            when {
                e.sha256.isBlank() -> DownloadResult.Unverified(t, actual, "No official hash in the manifest yet.")
                actual.equals(e.sha256, ignoreCase = true) -> DownloadResult.Installed(t)
                else -> { t.delete(); DownloadResult.Failed("Checksum mismatch; file deleted.") }
            }
        } catch (ex: Exception) {
            DownloadResult.Failed(ex.message ?: ex.javaClass.simpleName)
        }
    }
}
