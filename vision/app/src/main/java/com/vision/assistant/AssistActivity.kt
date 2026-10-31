package com.vision.assistant

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.vision.assistant.privacy.LockPrefs
import com.vision.assistant.ui.AiScreen
import com.vision.assistant.ui.VisionTheme

/**
 * "Call VISION" entry point. Opens a glowing assistant sheet over whatever is on screen, including the lock screen
 * (when enabled in Settings). Reachable from the Quick Settings tile “Ask VISION”, the voice-session notification,
 * and as the phone's digital-assistant app (Settings → Apps → Default apps → Digital assistant app).
 * It never bypasses the lock screen: actions that need an unlocked phone ask the owner to unlock first.
 */
class AssistActivity : ComponentActivity() {
    private val unlockFor = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VisionApp
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (LockPrefs.load(this).overLockScreen) {
            if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
            else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        } else if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissCancelled() { finish() }
                override fun onDismissError() { finish() }
            })
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            VisionTheme {
                AiScreen(app, overlay = true, autoListen = true, onUnlockRequest = { text -> askUnlock(km, text) }, unlockedText = unlockFor.value, onClose = { finish() })
            }
        }
    }

    private fun askUnlock(km: KeyguardManager, text: String) {
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { unlockFor.value = text }
        })
    }
}
