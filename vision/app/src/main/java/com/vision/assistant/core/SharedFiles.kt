package com.vision.assistant.core

import android.content.Context
import java.io.File

/** The only folder the FileProvider exposes. Files placed here for sharing are removed again after an hour so exported memory or projects do not linger in the cache. */
object SharedFiles {
    private const val MAX_AGE_MS = 60 * 60 * 1000L
    fun dir(c: Context): File = File(c.cacheDir, "shared").apply { mkdirs() }
    fun file(c: Context, name: String): File = File(dir(c), name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "file" })
    fun sweep(c: Context, now: Long = System.currentTimeMillis()) {
        try { dir(c).listFiles()?.filter { it.isFile && now - it.lastModified() > MAX_AGE_MS }?.forEach { it.delete() } } catch (_: Exception) { }
    }
}
