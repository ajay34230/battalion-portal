package com.vision.assistant

import com.vision.assistant.ai.MultiStep
import org.junit.Assert.*
import org.junit.Test

class MultiStepTest {
    @Test fun splitsRecognisedCommands() = assertEquals(2, MultiStep.split("what's the time then what is my battery").size)
    @Test fun hinglishSeparator() = assertEquals(2, MultiStep.split("time kya hai phir battery batao").size)
    @Test fun doesNotSplitNormalChat() = assertEquals(1, MultiStep.split("tell me a story and then explain why the sky is blue").size)
    @Test fun singleCommandUntouched() = assertEquals(1, MultiStep.split("open youtube").size)
}
