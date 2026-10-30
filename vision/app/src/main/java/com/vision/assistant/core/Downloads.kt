package com.vision.assistant.core

import com.vision.assistant.VisionApp
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** App-scoped downloads: they keep running when the user leaves the screen. Interrupted downloads resume from the .part file. */
object Downloads {
    val status = MutableStateFlow<Map<String, String>>(emptyMap())
    private val running = mutableSetOf<String>()

    fun manager(app: VisionApp) = ModelManager(File(app.filesDir, "models"), app.deviceProfile)

    fun start(app: VisionApp, e: ResourceEntry) {
        synchronized(running) { if (!running.add(e.id)) return }
        app.appScope.launch {
            val mgr = manager(app)
            setStatus(e.id, "downloading…")
            val r = mgr.download(e) { d, t -> setStatus(e.id, if (t > 0) "${d * 100 / t}% (${d / 1_048_576} MB)" else "${d / 1_048_576} MB") }
            setStatus(e.id, when (r) {
                is DownloadResult.Installed -> "installed & verified"
                is DownloadResult.Unverified -> "downloaded but UNVERIFIED"
                is DownloadResult.Failed -> "failed: ${r.reason}"
            })
            synchronized(running) { running.remove(e.id) }
            if (e.kind == ResourceKind.LLM) app.engines.invalidate()
        }
    }

    private fun setStatus(id: String, s: String) = status.update { it + (id to s) }
}
