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
        setContent {
            VisionTheme { AuroraBackground { VisionNav(app) } }
        }
    }
}
