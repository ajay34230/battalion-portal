package com.vision.assistant

import com.vision.assistant.code.TermuxRunner
import com.vision.assistant.core.AesGcm
import com.vision.assistant.core.SafeZip
import com.vision.assistant.data.BackupRotation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class SafeZipTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun rejectsEscapingNames() {
        val base = tmp.newFolder("b")
        for (bad in listOf("../x", "a/../../x", "/etc/passwd", "C:\\x", "a\\..\\..\\x", "")) assertNull(bad, SafeZip.resolve(base, bad))
        assertNotNull(SafeZip.resolve(base, "a/b/c.txt"))
    }
    @Test fun rejectsSymlinkEscape() {
        val base = tmp.newFolder("b"); val outside = tmp.newFolder("o")
        try { java.nio.file.Files.createSymbolicLink(java.io.File(base, "link").toPath(), outside.toPath()) } catch (_: Exception) { return }
        assertNull(SafeZip.resolve(base, "link/evil.txt"))
    }
    @Test fun enforcesEntryTotalAndRatio() {
        val l = SafeZip.Limits(maxEntries = 2, maxEntryBytes = 100, maxTotalBytes = 150, maxRatio = 10)
        val b = SafeZip.Budget(l); b.entry(); b.entry()
        assertThrows(SafeZip.ZipRejected::class.java) { b.entry() }
        assertThrows(SafeZip.ZipRejected::class.java) { SafeZip.Budget(l).add(101, 101) }
        val t = SafeZip.Budget(l); t.add(100, 100)
        assertThrows(SafeZip.ZipRejected::class.java) { t.add(60, 60) }
        assertThrows(SafeZip.ZipRejected::class.java) { SafeZip.Budget(l, archiveBytes = 5).add(60, 60) } // 60 > 5*10
    }
    @Test fun copyEntryStopsAtLimit() {
        val b = SafeZip.Budget(SafeZip.Limits(10, 50, 50, 1000))
        assertThrows(SafeZip.ZipRejected::class.java) { SafeZip.copyEntry(ByteArray(500).inputStream(), java.io.ByteArrayOutputStream(), b) }
    }
}

class AesGcmTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun roundTrip() { val k = key(); val t = AesGcm.seal(k, "voice 0.12,0.5".toByteArray()); assertTrue(t.startsWith("v1.")); assertEquals("voice 0.12,0.5", String(AesGcm.open(k, t)!!)) }
    @Test fun wrongKeyAndTamperAndGarbageGiveNull() {
        val k = key(); val t = AesGcm.seal(k, "secret".toByteArray())
        assertNull(AesGcm.open(key(), t))
        val parts = t.split('.'); val ct = parts[2]; val flipped = (if (ct[0] == 'A') 'B' else 'A') + ct.substring(1)
        assertNull(AesGcm.open(k, "v1.${parts[1]}.$flipped")); assertNull(AesGcm.open(k, "nonsense")); assertNull(AesGcm.open(k, "v2.a.b"))
    }
    @Test fun sameTextGivesDifferentTokens() { val k = key(); assertNotEquals(AesGcm.seal(k, "a".toByteArray()), AesGcm.seal(k, "a".toByteArray())) }
}

class BackupRotationTest {
    @Test fun keepsNewestAndPrunesOldest() {
        val names = listOf(3L, 1L, 5L, 2L, 4L, 6L, 7L).map { "vision.db.upgrade.${BackupRotation.stamp(it)}.bak" }
        val gone = BackupRotation.toPrune(names, 5)
        assertEquals(2, gone.size); assertTrue(gone.all { it.contains(BackupRotation.stamp(1)) || it.contains(BackupRotation.stamp(2)) })
        assertTrue(BackupRotation.toPrune(names.take(3), 5).isEmpty())
    }
}

class TermuxSafetyTest {
    private val files = mapOf("a.py" to "print(1)\n", "src/b.txt" to "x")
    @Test fun neverUsesRecursiveForceDelete() {
        for (s in listOf(TermuxRunner.script("demo", files, "true")!!, TermuxRunner.gitScript("demo", files, "git status")!!)) assertFalse(s.contains("rm -rf"))
    }
    @Test fun stagesThenBacksUpBeforeSwap() {
        val s = TermuxRunner.script("demo", files, "python3 'a.py'")!!
        val staging = s.indexOf("mkdir \"\$n\""); val backup = s.indexOf(".vision-backups") ; val move = s.indexOf("mv \"\$d\"")
        assertTrue(staging in 0 until move); assertTrue(backup >= 0); assertTrue(s.indexOf("printf %s") in staging until move)
        assertTrue(s.startsWith("set -e"))
    }
    @Test fun gitScriptKeepsGitAndTakesBackup() {
        val s = TermuxRunner.gitScript("demo", files, "git status")!!
        assertTrue(s.contains("--exclude=./.git")); assertTrue(s.indexOf("tar -cf") < s.indexOf("find ."))
    }
    @Test fun badSlugsAreRefused() {
        for (bad in listOf("", "../x", "a b", "a;rm", "-x", "x/y", "a".repeat(41), "\$(id)")) { assertNull(bad, TermuxRunner.script(bad, files, "true")); assertNull(bad, TermuxRunner.gitScript(bad, files, "true")) }
        assertNotNull(TermuxRunner.script("my-proj_1", files, "true"))
    }
}
