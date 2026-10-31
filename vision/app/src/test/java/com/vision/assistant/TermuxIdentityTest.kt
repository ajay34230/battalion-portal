package com.vision.assistant

import com.vision.assistant.ai.Identity
import com.vision.assistant.ai.Intent
import com.vision.assistant.ai.IntentParser
import com.vision.assistant.ai.Lang
import com.vision.assistant.termux.CommandPolicy
import com.vision.assistant.termux.Validation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxIdentityTest {
    private val home = CommandPolicy.HOME

    @Test fun torchRunsWithoutConfirmation() {
        val v = CommandPolicy.validateLine("termux-torch on") as Validation.Ok
        assertFalse(v.sensitive)
    }

    @Test fun textArgumentsMayContainPunctuation() {
        val v = CommandPolicy.validateLine("termux-tts-speak 'hello; world & more'") as Validation.Ok
        assertEquals(listOf("hello; world & more"), v.args)
    }

    @Test fun privateAndOutgoingActionsNeedConfirmation() {
        assertTrue((CommandPolicy.validateLine("termux-location") as Validation.Ok).sensitive)
        assertTrue((CommandPolicy.validateLine("termux-clipboard-get") as Validation.Ok).sensitive)
        val sms = CommandPolicy.validateLine("termux-sms-send -n +911234567890 \"hi\"") as Validation.Ok
        assertTrue(sms.sensitive && sms.high)
    }

    @Test fun pathsStayInsideApprovedFolders() {
        assertTrue(CommandPolicy.validateLine("rm /etc/passwd") is Validation.Rejected)
        assertTrue(CommandPolicy.validateLine("rm $home/vision-output") is Validation.Rejected)
        assertTrue(CommandPolicy.validateLine("rm $home/vision-output/photo.jpg") is Validation.Ok)
        assertTrue(CommandPolicy.validateLine("termux-camera-photo -c 0 $home/storage/x.jpg") is Validation.Rejected)
    }

    @Test fun rawShellNeedsAdvancedModeAndAlwaysConfirms() {
        assertTrue(CommandPolicy.validateLine("echo hi | wc -c") is Validation.Rejected)
        val v = CommandPolicy.validateLine("echo hi | wc -c", advanced = true) as Validation.Ok
        assertEquals("bash", v.binary); assertTrue(v.sensitive && v.high)
    }

    @Test fun tokenizerHandlesQuotes() {
        assertEquals(listOf("a", "b c", "d"), CommandPolicy.tokenize("a 'b c' \"d\""))
        assertEquals(null, CommandPolicy.tokenize("a 'b"))
    }

    @Test fun phoneControlPhrasesBecomeTermuxCommands() {
        assertEquals(Intent.Termux("termux-torch on"), IntentParser.parse("torch on"))
        assertEquals(Intent.Termux("termux-torch off"), IntentParser.parse("flashlight band karo"))
        assertEquals(Intent.Termux("termux-volume music 7"), IntentParser.parse("volume 7"))
        assertEquals(Intent.Termux("termux-location"), IntentParser.parse("meri location batao"))
        assertEquals(Intent.Termux("uptime"), IntentParser.parse("termux uptime"))
    }

    @Test fun knowsItsCreator() {
        assertTrue(Identity.isCreatorQuestion("who made you?"))
        assertTrue(Identity.isCreatorQuestion("tumhe kisne banaya"))
        assertTrue(Identity.isCreatorQuestion("तुम्हें किसने बनाया"))
        assertFalse(Identity.isCreatorQuestion("what is the time"))
        for (l in Lang.values()) assertTrue(Identity.creatorAnswer(l).isNotBlank())
        assertTrue(Identity.creatorAnswer(Lang.EN).contains("Ajay Singh Shekhawat"))
    }
}
