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
        com.vision.assistant.health.CrashLog.install(this)
        com.vision.assistant.tasks.ReminderScheduler.ensureChannels(this)
        try { com.vision.assistant.tasks.Routines.scheduleAll(this) } catch (_: Throwable) {}
        try { com.vision.assistant.finance.PaymentReview.schedule(this) } catch (_: Throwable) {}
        // Load an installed language model in the background so the first reply is not slow.
        appScope.launch {
            // VISION always knows who built it.
            val sp = getSharedPreferences("vision_prefs", MODE_PRIVATE)
            if (!sp.getBoolean("creator_seeded", false)) {
                database.memory().insert(com.vision.assistant.data.MemoryItem(category = "creator", content = com.vision.assistant.ai.Identity.MEMORY_FACT))
                sp.edit().putBoolean("creator_seeded", true).apply()
            }
            try { com.vision.assistant.ai.Learning.refresh(database.memory()) } catch (_: Throwable) {}
            try { engines.current() } catch (_: Throwable) {}
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 15 = running critically low; >= 60 = our process is a candidate to be killed. Free the model so Android keeps VISION alive.
        if (level == 15 || level >= 60) appScope.launch { try { engines.unload() } catch (_: Throwable) {} }
    }

    val database: VisionDatabase by lazy { VisionDatabase.build(this) }
    val deviceProfile: DeviceProfile by lazy { DeviceProfiler(this).profile() }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val engines: EngineProvider by lazy { EngineProvider(this) }
}
