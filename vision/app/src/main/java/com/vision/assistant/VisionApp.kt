package com.vision.assistant

import android.app.Application
import com.vision.assistant.ai.EngineProvider
import com.vision.assistant.core.DeviceProfile
import com.vision.assistant.core.DeviceProfiler
import com.vision.assistant.data.VisionDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VisionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load an installed language model in the background so the first reply is not slow.
        appScope.launch { try { engines.current() } catch (_: Throwable) {} }
    }

    val database: VisionDatabase by lazy { VisionDatabase.build(this) }
    val deviceProfile: DeviceProfile by lazy { DeviceProfiler(this).profile() }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val engines: EngineProvider by lazy { EngineProvider(this) }
}
