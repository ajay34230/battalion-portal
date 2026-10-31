package com.vision.assistant.data

import android.content.Context
import java.io.File

/** Pure file rotation so it can be unit tested without Android. Names end in a millisecond timestamp, so lexical order is age order. */
object BackupRotation {
    fun stamp(now: Long = System.currentTimeMillis()) = now.toString().padStart(15, '0')
    fun toPrune(names: List<String>, keep: Int): List<String> = names.sorted().dropLast(keep.coerceAtLeast(0))
}

/**
 * Database protection: copies vision.db before an upgrade, never deletes a database it cannot open (it is moved to the backup folder),
 * and applies a restore that the user requested. Nothing here removes user data without leaving a copy.
 */
object DbSafety {
    const val KEEP = 5
    private const val DB = "vision.db"
    private fun backupDir(c: Context) = File(c.filesDir, "db_backups").apply { mkdirs() }
    private fun sp(c: Context) = c.getSharedPreferences("vision_db", Context.MODE_PRIVATE)

    private fun dbFiles(c: Context) = listOf("", "-wal", "-shm").map { c.getDatabasePath(DB).let { f -> File(f.path + it) } }

    fun userVersion(f: File): Int = try {
        android.database.sqlite.SQLiteDatabase.openDatabase(f.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { it.version }
    } catch (_: Exception) { -1 }

    /** Folds the write-ahead log into the main file so a copy of vision.db alone is complete. */
    private fun checkpoint(f: File) {
        try {
            android.database.sqlite.SQLiteDatabase.openDatabase(f.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        } catch (_: Exception) { }
    }

    private fun copyTo(c: Context, tag: String): File? {
        val main = c.getDatabasePath(DB); if (!main.exists()) return null
        checkpoint(main)
        val dest = File(backupDir(c), "$DB.$tag.${BackupRotation.stamp()}.bak")
        return try { main.copyTo(dest, overwrite = false); rotate(c); dest } catch (_: Exception) { null }
    }

    private fun rotate(c: Context) {
        val dir = backupDir(c)
        for (tag in listOf("upgrade", "corrupt", "pre-restore", "manual")) {
            val names = dir.list { _, n -> n.startsWith("$DB.$tag.") }?.toList().orEmpty()
            BackupRotation.toPrune(names, KEEP).forEach { File(dir, it).delete() }
        }
    }

    /** Called before Room opens the file. Backs up when the stored schema version is older than [currentVersion] and applies a pending restore. */
    fun beforeOpen(c: Context, currentVersion: Int) {
        applyPendingRestore(c)
        val main = c.getDatabasePath(DB)
        if (main.exists()) { val v = userVersion(main); if (v in 1 until currentVersion) copyTo(c, "upgrade") }
    }

    /** Verifies the opened database. Returns null when healthy, otherwise a description of the problem. */
    fun check(db: androidx.sqlite.db.SupportSQLiteDatabase): String? = try {
        db.query("PRAGMA quick_check").use { cur -> if (cur.moveToFirst() && cur.getString(0) == "ok") null else "Database integrity check failed." }
    } catch (e: Exception) { e.message ?: "Database check failed." }

    /** Moves an unusable database aside (kept for recovery, never deleted) so the app can start with a fresh one. */
    fun quarantine(c: Context, reason: String) {
        val dir = backupDir(c); val tag = BackupRotation.stamp()
        for (f in dbFiles(c)) if (f.exists()) { try { f.copyTo(File(dir, "$DB.corrupt.$tag${f.name.removePrefix(DB)}.bak")); f.delete() } catch (_: Exception) { } }
        sp(c).edit().putString("notice", "VISION could not open its database ($reason). The old file was kept as a backup and a fresh database was started.").apply()
        rotate(c)
    }

    fun notice(c: Context): String? = sp(c).getString("notice", null)
    fun clearNotice(c: Context) { sp(c).edit().remove("notice").apply() }

    fun backups(c: Context): List<File> = backupDir(c).listFiles { f -> f.name.endsWith(".bak") && f.name.startsWith("$DB.") }?.sortedByDescending { it.name }.orEmpty()

    /** Takes a manual backup now. */
    fun backupNow(c: Context): File? = copyTo(c, "manual")

    /** Schedules a restore of [backup] for the next start (the database is open now). The current database is saved first. */
    fun requestRestore(c: Context, backup: File) { sp(c).edit().putString("restore", backup.name).apply() }

    private fun applyPendingRestore(c: Context) {
        val name = sp(c).getString("restore", null) ?: return
        sp(c).edit().remove("restore").apply()
        val src = File(backupDir(c), name); if (!src.isFile || userVersion(src) < 1) return
        copyTo(c, "pre-restore")
        for (f in dbFiles(c)) f.delete()
        try { src.copyTo(c.getDatabasePath(DB), overwrite = true) } catch (_: Exception) { }
    }
}
