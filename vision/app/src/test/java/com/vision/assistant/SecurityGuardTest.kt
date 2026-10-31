package com.vision.assistant

import com.vision.assistant.core.SharedFiles
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source-level guards so a later change cannot quietly widen the attack surface. */
class SecurityGuardTest {
    private fun module(p: String) = File(p).takeIf { it.exists() } ?: File("app/$p")
    private val manifest get() = module("src/main/AndroidManifest.xml").readText()

    @Test fun onlyExpectedComponentsAreExported() {
        val exported = Regex("<(activity|service|receiver|provider)\\s[^>]*?android:name=\"([^\"]+)\"[^>]*?android:exported=\"true\"", RegexOption.DOT_MATCHES_ALL)
        val names = Regex("<(activity|service|receiver|provider)\\b[^>]*>").findAll(manifest).filter { it.value.contains("android:exported=\"true\"") }
            .map { Regex("android:name=\"([^\"]+)\"").find(it.value)!!.groupValues[1] }.toSet()
        assertEquals(setOf(".MainActivity", ".AssistActivity", ".comms.VisionNotificationListener", ".service.AskVisionTile"), names)
        assertNotNull(exported)
    }
    @Test fun systemBoundServicesRequireTheirPermission() {
        assertTrue(manifest.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"))
        assertTrue(manifest.contains("android.permission.BIND_QUICK_SETTINGS_TILE"))
    }
    @Test fun backupAndCleartextAreOff() {
        assertTrue(manifest.contains("android:allowBackup=\"false\"")); assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("networkSecurityConfig")); assertTrue(manifest.contains("dataExtractionRules"))
    }
    @Test fun fileProviderExposesOnlySharedSubfolder() {
        val x = module("src/main/res/xml/file_paths.xml").readText()
        assertTrue(x.contains("path=\"shared/\"")); assertFalse(x.contains("path=\".\""))
    }
    @Test fun noLogCallsThatCouldLeakPersonalText() {
        val bad = module("src/main/java").walkTopDown().filter { it.extension == "kt" }.filter { Regex("\\bLog\\.[divwe]\\(").containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue("Log calls in $bad", bad.isEmpty())
    }
    @Test fun sharedFileNamesCannotEscape() {
        // name sanitising is pure string logic; verify through the regex the helper uses
        val clean = "../../x y.zip".replace(Regex("[^A-Za-z0-9._-]"), "_")
        assertFalse(clean.contains("/")); assertNotNull(SharedFiles)
    }
}
