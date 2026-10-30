package com.vision.assistant.privacy

import com.vision.assistant.ai.Intent

/** What VISION may do while the phone is locked. It never bypasses the lock screen: it only refuses. */
object LockPolicy {
    fun allowedWhenLocked(i: Intent): Boolean = when (i) {
        Intent.AskTime, Intent.AskBattery -> true
        is Intent.SetReminder, is Intent.Remember -> true
        is Intent.Search -> true
        is Intent.Chat -> true
        Intent.ListTasks, is Intent.Recall -> false      // may reveal personal details
        is Intent.OpenApp, is Intent.Call -> false       // needs an unlocked device
        else -> false                                      // notifications, mail, WhatsApp, anything new: deny by default
    }
}

object SensitiveText {
    private val patterns = listOf(
        Regex("\\b\\d{4,8}\\b.{0,40}(otp|one[- ]time|verification|code|cvv|pin)", RegexOption.IGNORE_CASE),
        Regex("(otp|one[- ]time|verification code|security code|cvv|pin|password).{0,40}\\b\\d{4,8}\\b", RegexOption.IGNORE_CASE),
        Regex("(a/c|account|acct|card).{0,25}(x{2,}|\\*{2,})?\\d{4}", RegexOption.IGNORE_CASE),
        Regex("(debited|credited|upi|txn|transaction).{0,60}(rs\\.?|inr|₹)", RegexOption.IGNORE_CASE),
    )
    fun isSensitive(text: String) = patterns.any { it.containsMatchIn(text) }
}
