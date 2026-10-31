package com.vision.assistant.core

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Shared ZIP safety rules: path escape, entry count, per-entry size, total size and expansion ratio. Throws [ZipRejected] instead of silently accepting a hostile archive. */
object SafeZip {
    class ZipRejected(msg: String) : Exception(msg)

    class Limits(val maxEntries: Int, val maxEntryBytes: Long, val maxTotalBytes: Long, val maxRatio: Int)

    /** Resolves an archive entry name inside [base]. Returns null for absolute, drive, NUL or ".." names or anything that resolves outside [base] (symlinks included, via canonical paths). */
    fun resolve(base: File, name: String): File? {
        val n = name.replace('\\', '/')
        if (n.isEmpty() || n.startsWith("/") || n.contains('\u0000') || Regex("^[A-Za-z]:").containsMatchIn(n)) return null
        if (n.split('/').any { it == ".." }) return null
        val target = File(base, n)
        val root = base.canonicalPath + File.separator
        val canon = target.canonicalPath
        return if (canon == base.canonicalPath || canon.startsWith(root)) target else null
    }

    /** Counts what has been extracted so far and throws when a limit is crossed. [archiveBytes] is the archive size on disk (0 = unknown, ratio not checked). */
    class Budget(private val limits: Limits, private val archiveBytes: Long = 0) {
        var entries = 0; private set
        var total = 0L; private set
        fun entry() { if (++entries > limits.maxEntries) throw ZipRejected("Archive has more than ${limits.maxEntries} entries.") }
        fun add(n: Int, entryBytes: Long) {
            total += n
            if (entryBytes > limits.maxEntryBytes) throw ZipRejected("An entry is larger than the allowed ${limits.maxEntryBytes} bytes.")
            if (total > limits.maxTotalBytes) throw ZipRejected("Archive expands beyond ${limits.maxTotalBytes} bytes.")
            if (archiveBytes > 0 && total > archiveBytes * limits.maxRatio) throw ZipRejected("Archive expansion ratio is suspiciously high.")
        }
    }

    /** Copies the current entry to [out] while enforcing the budget. */
    fun copyEntry(input: InputStream, out: OutputStream, budget: Budget) {
        val buf = ByteArray(16 * 1024); var entryBytes = 0L
        while (true) {
            val n = input.read(buf); if (n < 0) break
            entryBytes += n; budget.add(n, entryBytes); out.write(buf, 0, n)
        }
    }
}
