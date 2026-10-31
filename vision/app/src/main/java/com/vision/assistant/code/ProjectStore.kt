package com.vision.assistant.code

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One snapshot of a project that can be rolled back to. */
data class Snapshot(val id: String, val label: String, val time: Long)

/**
 * Local project storage. Everything lives under [root]; paths from the model or the user are validated so a project can never read or write outside its own folder.
 * Snapshots are plain copies in `.snapshots/` inside the project (excluded from ZIP export).
 */
class ProjectStore(val root: File) {
    init { root.mkdirs() }

    companion object {
        private val NAME = Regex("[A-Za-z0-9][A-Za-z0-9 _.-]{0,39}")
        const val MAX_FILE_BYTES = 512 * 1024
        const val MAX_FILES = 200
        fun validName(n: String) = NAME.matches(n.trim())
        /** Relative, forward-slash path with no traversal, no hidden folders, no absolute roots. */
        fun safeRelative(p: String): String? {
            val s = p.trim().replace('\\', '/').trimStart('/')
            if (s.isEmpty() || s.length > 160) return null
            val parts = s.split('/')
            if (parts.any { it.isEmpty() || it == "." || it == ".." || it.startsWith(".") }) return null
            if (parts.any { !Regex("[A-Za-z0-9 _.@+()-]+").matches(it) }) return null
            return parts.joinToString("/")
        }
        fun slug(n: String) = n.trim().replace(Regex("\\s+"), "-")
    }

    fun list(): List<String> = root.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.map { it.name }?.sorted().orEmpty()
    fun dir(name: String): File = File(root, slug(name))
    fun exists(name: String) = dir(name).isDirectory

    fun create(name: String): Result<File> {
        if (!validName(name)) return Result.failure(IllegalArgumentException("Use letters, numbers, spaces, - or _ (max 40 characters)."))
        val d = dir(name)
        if (d.exists()) return Result.failure(IllegalStateException("A project named \"$name\" already exists."))
        d.mkdirs(); return Result.success(d)
    }

    fun delete(name: String): Boolean = exists(name) && dir(name).deleteRecursively()

    fun files(name: String): List<String> {
        val d = dir(name)
        return d.walkTopDown().filter { it.isFile && !it.relativeTo(d).path.startsWith(".") }.map { it.relativeTo(d).path.replace('\\', '/') }.sorted().toList()
    }

    fun read(name: String, path: String): String? {
        val rel = safeRelative(path) ?: return null
        val f = File(dir(name), rel)
        return if (f.isFile && f.length() <= MAX_FILE_BYTES) f.readText() else null
    }

    fun write(name: String, path: String, content: String): Result<Unit> {
        val rel = safeRelative(path) ?: return Result.failure(IllegalArgumentException("Unsafe file path: $path"))
        if (content.toByteArray().size > MAX_FILE_BYTES) return Result.failure(IllegalArgumentException("$rel is larger than 512 KB."))
        if (!exists(name)) return Result.failure(IllegalStateException("No such project."))
        if (files(name).size >= MAX_FILES && !File(dir(name), rel).exists()) return Result.failure(IllegalStateException("Too many files (max $MAX_FILES)."))
        val f = File(dir(name), rel)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return Result.success(Unit)
    }

    fun deleteFile(name: String, path: String): Boolean {
        val rel = safeRelative(path) ?: return false
        return File(dir(name), rel).takeIf { it.isFile }?.delete() ?: false
    }

    /** Writes the ZIP of all project files (not snapshots) to [out]. Returns the number of files. */
    fun exportZip(name: String, out: OutputStream): Int {
        var n = 0
        ZipOutputStream(out).use { z ->
            for (p in files(name)) {
                z.putNextEntry(ZipEntry("${slug(name)}/$p"))
                z.write(File(dir(name), p).readBytes()); z.closeEntry(); n++
            }
        }
        return n
    }

    /** Imports a ZIP into a new project. Entries with unsafe paths are skipped. Returns the created project's file count. */
    fun importZip(name: String, input: java.io.InputStream): Result<Int> {
        val created = create(name); if (created.isFailure) return Result.failure(created.exceptionOrNull()!!)
        var n = 0
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory && n < MAX_FILES) {
                    val rel = safeRelative(e.name.substringAfter('/', e.name))
                    if (rel != null) { val bytes = readLimited(z, MAX_FILE_BYTES + 1); if (bytes.size <= MAX_FILE_BYTES) { File(dir(name), rel).apply { parentFile?.mkdirs(); writeBytes(bytes) }; n++ } }
                }
                e = z.nextEntry
            }
        }
        return Result.success(n)
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192); var total = 0
        while (total < limit) { val n = input.read(chunk, 0, minOf(chunk.size, limit - total)); if (n < 0) break; buf.write(chunk, 0, n); total += n }
        return buf.toByteArray()
    }

    // ---- snapshots (rollback) ----
    private fun snapRoot(name: String) = File(dir(name), ".snapshots")

    fun snapshot(name: String, label: String): Snapshot {
        val id = System.currentTimeMillis().toString()
        val target = File(snapRoot(name), id)
        for (p in files(name)) File(target, p).apply { parentFile?.mkdirs(); writeBytes(File(dir(name), p).readBytes()) }
        File(target, ".label").apply { parentFile?.mkdirs(); writeText(label) }
        // keep the newest 15
        snapRoot(name).listFiles()?.sortedBy { it.name }?.dropLast(15)?.forEach { it.deleteRecursively() }
        return Snapshot(id, label, id.toLong())
    }

    fun snapshots(name: String): List<Snapshot> =
        snapRoot(name).listFiles { f -> f.isDirectory }?.sortedByDescending { it.name }?.map { Snapshot(it.name, File(it, ".label").takeIf { l -> l.exists() }?.readText().orEmpty(), it.name.toLongOrNull() ?: 0) }.orEmpty()

    /** Restores a snapshot. The current state is snapshotted first, so a rollback can itself be undone. */
    fun rollback(name: String, id: String): Boolean {
        val src = File(snapRoot(name), id).takeIf { it.isDirectory } ?: return false
        snapshot(name, "Before rollback")
        for (p in files(name)) File(dir(name), p).delete()
        src.walkTopDown().filter { it.isFile && !it.relativeTo(src).path.startsWith(".") }.forEach { f ->
            File(dir(name), f.relativeTo(src).path).apply { parentFile?.mkdirs(); writeBytes(f.readBytes()) }
        }
        return true
    }
}
