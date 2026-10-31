package com.vision.assistant

import com.vision.assistant.core.InstallState.*
import com.vision.assistant.core.ModelLifecycle
import com.vision.assistant.core.ModelStatus.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelLifecycleTest {
    private fun s(i: com.vision.assistant.core.InstallState, failed: Boolean = false, loaded: Boolean = false, answered: Boolean = false) = ModelLifecycle.status(i, failed, loaded, answered)
    @Test fun walksThroughTheStates() {
        assertEquals(NOT_DOWNLOADED, s(NOT_INSTALLED)); assertEquals(DOWNLOADED, s(DOWNLOADED_UNVERIFIED))
        assertEquals(INSTALLED, s(INSTALLED)); assertEquals(LOADED, s(INSTALLED, loaded = true)); assertEquals(READY, s(INSTALLED, loaded = true, answered = true))
    }
    @Test fun failureWinsAndAnsweredAloneIsNotReady() {
        assertEquals(FAILED, s(INSTALLED, failed = true, loaded = true, answered = true))
        assertEquals(INSTALLED, s(INSTALLED, answered = true))
        assertEquals(DOWNLOADED, s(DOWNLOADED_UNVERIFIED, loaded = true, answered = true))
    }
}
