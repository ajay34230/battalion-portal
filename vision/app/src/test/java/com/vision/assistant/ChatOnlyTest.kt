package com.vision.assistant

import com.vision.assistant.ai.ChatOnly
import org.junit.Assert.*
import org.junit.Test

class ChatOnlyTest {
    @Test fun smallTalkSkipsPlanner() { listOf("hi", "Hello!", "how are you", "kaise ho", "thank you", "who are you", "hey vision").forEach { assertTrue(it, ChatOnly.matches(it)) } }
    @Test fun explainQuestionsSkipPlanner() { assertTrue(ChatOnly.matches("explain how a rainbow forms")); assertTrue(ChatOnly.matches("why is the sky blue")) }
    @Test fun actionsAndLookupsStillGoThroughPlanner() {
        listOf("open whatsapp", "call mom", "remind me to drink water", "why is the weather bad today", "explain my notes", "what is the price of gold", "hi call mom", "mujhe 5 baje uthao", "").forEach { assertFalse(it, ChatOnly.matches(it)) }
    }
}
