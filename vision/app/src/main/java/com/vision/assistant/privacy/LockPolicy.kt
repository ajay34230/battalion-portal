package com.vision.assistant.privacy

import android.content.Context
import com.vision.assistant.ai.Intent

/** All limits are OFF by default: the owner adds limits in Settings. Android's own lock screen is never bypassed. */
data class LockSettings(
    val overLockScreen: Boolean = true,
    val limitsEnabled: Boolean = false,
    val allowApps: Boolean = true,
    val allowCalls: Boolean = true,
    val allowMessages: Boolean = true,
    val allowNotifications: Boolean = true,
    val allowPersonalData: Boolean = true,
    val readSensitiveAloud: Boolean = false,
)

object LockPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("vision_lock", Context.MODE_PRIVATE)
    fun load(ctx: Context): LockSettings = p(ctx).let {
        val d = LockSettings()
        LockSettings(
            it.getBoolean("overLock", d.overLockScreen), it.getBoolean("limits", d.limitsEnabled),
            it.getBoolean("apps", d.allowApps), it.getBoolean("calls", d.allowCalls), it.getBoolean("messages", d.allowMessages),
            it.getBoolean("notifs", d.allowNotifications), it.getBoolean("personal", d.allowPersonalData), it.getBoolean("sensitive", d.readSensitiveAloud),
        )
    }
    fun save(ctx: Context, s: LockSettings) {
        p(ctx).edit().putBoolean("overLock", s.overLockScreen).putBoolean("limits", s.limitsEnabled).putBoolean("apps", s.allowApps)
            .putBoolean("calls", s.allowCalls).putBoolean("messages", s.allowMessages).putBoolean("notifs", s.allowNotifications)
            .putBoolean("personal", s.allowPersonalData).putBoolean("sensitive", s.readSensitiveAloud).apply()
    }
}

object LockPolicy {
    fun allowedWhenLocked(i: Intent, s: LockSettings = LockSettings()): Boolean {
        if (!s.limitsEnabled) return true
        return when (i) {
            is Intent.OpenApp -> s.allowApps
            is Intent.Call -> s.allowCalls
            is Intent.WhatsAppDraft -> s.allowMessages
            is Intent.ReadNotifications, Intent.ReadEmail -> s.allowNotifications
            Intent.ListTasks, is Intent.Recall, is Intent.Remember -> s.allowPersonalData
            else -> true
        }
    }

    /** Android shows other apps / the dialer only on an unlocked phone, so the owner is asked to unlock (never bypassed). */
    fun needsUnlock(i: Intent) = i is Intent.OpenApp || i is Intent.Call || i is Intent.WhatsAppDraft
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
