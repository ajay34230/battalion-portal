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
        val sensitive = SensitiveText.isSensitive("$title $text") || Untrusted.looksLikeInjection(text)
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
