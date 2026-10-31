package com.vision.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vision.assistant.ui.AuroraBackground
import com.vision.assistant.ui.VisionNav
import com.vision.assistant.ui.VisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VisionApp
        if (com.vision.assistant.research.Prefs.wakeWord(this) && androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) com.vision.assistant.service.WakeWordService.start(this)
        setContent {
            VisionTheme { AuroraBackground { VisionNav(app, intent?.getStringExtra("route")) } }
        }
    }
}
