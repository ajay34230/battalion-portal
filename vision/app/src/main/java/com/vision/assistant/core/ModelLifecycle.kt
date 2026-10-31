package com.vision.assistant.core

/** What a language model is doing, in plain words. A model is only READY after it has really produced an answer on this phone in this session. */
enum class ModelStatus(val label: String, val explain: String) {
    NOT_DOWNLOADED("Not downloaded", "Tap Download to get it."),
    DOWNLOADED("Downloaded, not verified", "The file is there but its checksum has not been confirmed. Delete it and download again."),
    INSTALLED("Installed", "Verified and stored. It loads the first time it is needed."),
    LOADED("Loaded", "In memory, but it has not answered yet."),
    READY("Ready", "Loaded and has answered at least once since the app started."),
    FAILED("Failed on this phone", "It crashed, stalled or ran out of memory here, so it is skipped automatically. You can retry it from Models.");
}

object ModelLifecycle {
    fun status(install: InstallState, failed: Boolean, loaded: Boolean, answered: Boolean): ModelStatus = when {
        failed -> ModelStatus.FAILED
        install == InstallState.NOT_INSTALLED -> ModelStatus.NOT_DOWNLOADED
        install == InstallState.DOWNLOADED_UNVERIFIED -> ModelStatus.DOWNLOADED
        loaded && answered -> ModelStatus.READY
        loaded -> ModelStatus.LOADED
        else -> ModelStatus.INSTALLED
    }
}
