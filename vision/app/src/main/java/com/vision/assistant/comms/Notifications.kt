package com.vision.assistant.comms

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vision.assistant.VisionApp
import com.vision.assistant.data.NotificationLog
import com.vision.assistant.privacy.SensitiveText
import com.vision.assistant.tools.Untrusted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives notifications only after the user grants Notification access. Stores a short local history (max 100). */
class VisionNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val whole = "$title $text"
        if (com.vision.assistant.finance.PaymentParser.isCandidatePackage(sbn.packageName) || com.vision.assistant.finance.PaymentParser.looksLikeBankAlert(sbn.packageName, whole)) {
            com.vision.assistant.finance.PaymentParser.parse(title, text)?.let { pay ->
                val appRef = application as VisionApp
                scope.launch {
                    val dao = appRef.database.payments()
                    if (dao.duplicates(pay.direction.name, pay.amountPaise, pay.party, System.currentTimeMillis() - 120_000) == 0)
                        dao.insert(com.vision.assistant.data.PaymentRecord(direction = pay.direction.name, paise = pay.amountPaise, party = pay.party, bill = pay.bill, category = com.vision.assistant.finance.PaymentParser.suggestCategory(pay.party, pay.bill), source = sbn.packageName))
                }
            }
        }
        val sensitive = (SensitiveText.isSensitive("$title $text") && !com.vision.assistant.privacy.LockPrefs.load(this).readSensitiveAloud) || Untrusted.looksLikeInjection(text)
        val app = application as VisionApp
        scope.launch {
            app.database.notifications().insert(
                NotificationLog(pkg = sbn.packageName, title = if (sensitive) "[hidden]" else title.take(80), text = if (sensitive) "[sensitive content hidden]" else text.take(300), sensitive = sensitive),
            )
            app.database.notifications().trim()
        }
    }
}

object NotificationSummary {
    fun isWhatsApp(pkg: String) = pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b"
}
