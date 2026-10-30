package com.vision.assistant

import android.app.Application
import com.vision.assistant.core.DeviceProfile
import com.vision.assistant.core.DeviceProfiler
import com.vision.assistant.data.VisionDatabase

class VisionApp : Application() {
    val database: VisionDatabase by lazy { VisionDatabase.build(this) }
    val deviceProfile: DeviceProfile by lazy { DeviceProfiler(this).profile() }
}
