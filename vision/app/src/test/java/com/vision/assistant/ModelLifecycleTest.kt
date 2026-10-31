package com.vision.assistant

import com.vision.assistant.core.InstallState
import com.vision.assistant.core.ModelLifecycle
import com.vision.assistant.core.ModelStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelLifecycleTest {
    private fun s(i: InstallState, failed: Boolean = false, loaded: Boolean = false, answered: Boolean = false) = ModelLifecycle.status(i, failed, loaded, answered)
    @Test fun walksThroughTheStates() {
        assertEquals(ModelStatus.NOT_DOWNLOADED, s(InstallState.NOT_INSTALLED)); assertEquals(ModelStatus.DOWNLOADED, s(InstallState.DOWNLOADED_UNVERIFIED))
        assertEquals(ModelStatus.INSTALLED, s(InstallState.INSTALLED)); assertEquals(ModelStatus.LOADED, s(InstallState.INSTALLED, loaded = true)); assertEquals(ModelStatus.READY, s(InstallState.INSTALLED, loaded = true, answered = true))
    }
    @Test fun failureWinsAndAnsweredAloneIsNotReady() {
        assertEquals(ModelStatus.FAILED, s(InstallState.INSTALLED, failed = true, loaded = true, answered = true))
        assertEquals(ModelStatus.INSTALLED, s(InstallState.INSTALLED, answered = true))
        assertEquals(ModelStatus.DOWNLOADED, s(InstallState.DOWNLOADED_UNVERIFIED, loaded = true, answered = true))
    }
}
