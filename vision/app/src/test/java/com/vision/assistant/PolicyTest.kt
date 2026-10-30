package com.vision.assistant

import com.vision.assistant.ai.Intent
import com.vision.assistant.privacy.LockPolicy
import com.vision.assistant.privacy.SensitiveText
import com.vision.assistant.termux.CommandPolicy
import com.vision.assistant.termux.Validation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test fun lockedAllowsTimeDeniesOpenApp() {
        assertTrue(LockPolicy.allowedWhenLocked(Intent.AskTime)); assertFalse(LockPolicy.allowedWhenLocked(Intent.OpenApp("x")))
        assertFalse(LockPolicy.allowedWhenLocked(Intent.Recall("pin")))
    }

    @Test fun otpIsSensitive() {
        assertTrue(SensitiveText.isSensitive("123456 is your verification code"))
        assertTrue(SensitiveText.isSensitive("Your OTP is 884213, do not share"))
        assertFalse(SensitiveText.isSensitive("Are we meeting at 5 tomorrow?"))
    }

    @Test fun termuxAllowsKnown() {
        assertTrue(CommandPolicy.validate("date", emptyList()) is Validation.Ok)
        val v = CommandPolicy.validate("python3", listOf("backup_notes.py")) as Validation.Ok
        assertTrue(v.sensitive)
    }

    @Test fun termuxRejectsInjection() {
        assertTrue(CommandPolicy.validate("ls", listOf("${CommandPolicy.HOME}/storage; rm -rf /")) is Validation.Rejected)
        assertTrue(CommandPolicy.validate("rm", listOf("-rf", "/")) is Validation.Rejected)
        assertTrue(CommandPolicy.validate("ls", listOf("${CommandPolicy.HOME}/storage/../../x")) is Validation.Rejected)
        assertTrue(CommandPolicy.validate("python3", listOf("../evil.py")) is Validation.Rejected)
        assertEquals(Validation.Rejected("Path not allowed."), CommandPolicy.validate("ls", listOf("/etc")))
    }
}
