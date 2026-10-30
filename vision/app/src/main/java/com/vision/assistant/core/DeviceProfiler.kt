package com.vision.assistant.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

enum class Tier { LITE, STANDARD, PERFORMANCE }

data class DeviceProfile(
    val totalRamMb: Long,
    val freeStorageMb: Long,
    val abis: List<String>,
    val sdk: Int,
    val cores: Int,
    val is64Bit: Boolean,
    val tier: Tier,
)

/** Pure policy so it can be unit tested without Android. See docs/ANDROID_COMPATIBILITY.md. */
object TierPolicy {
    fun pick(totalRamMb: Long, is64Bit: Boolean): Tier = when {
        !is64Bit || totalRamMb < 3_000 -> Tier.LITE
        totalRamMb < 6_000 -> Tier.STANDARD
        else -> Tier.PERFORMANCE
    }

    /** A model fits only if it uses <=40% of RAM and leaves a 1 GB storage reserve. */
    fun modelFits(modelSizeMb: Long, totalRamMb: Long, freeStorageMb: Long): Boolean =
        modelSizeMb * 100 <= totalRamMb * 40 && modelSizeMb + 1_024 <= freeStorageMb
}

class DeviceProfiler(private val context: Context) {
    fun profile(): DeviceProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamMb = info.totalMem / (1024 * 1024)
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeMb = stat.availableBytes / (1024 * 1024)
        val abis = Build.SUPPORTED_ABIS.toList()
        val is64 = abis.any { it.contains("64") }
        return DeviceProfile(
            totalRamMb = totalRamMb,
            freeStorageMb = freeMb,
            abis = abis,
            sdk = Build.VERSION.SDK_INT,
            cores = Runtime.getRuntime().availableProcessors(),
            is64Bit = is64,
            tier = TierPolicy.pick(totalRamMb, is64),
        )
    }
}
