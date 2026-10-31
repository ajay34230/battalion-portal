package com.vision.assistant

import com.vision.assistant.ai.Learning
import org.junit.Assert.*
import org.junit.Test

class LearningTest {
    private fun cats(t: String) = Learning.extract(t).map { it.category to it.content }
    @Test fun learnsName() = assertTrue(cats("my name is ajay").contains("fact" to "User's name is Ajay"))
    @Test fun learnsHinglishName() = assertTrue(cats("mera naam Ajay hai").any { it.second == "User's name is Ajay" })
    @Test fun learnsPerson() = assertTrue(cats("my mother is sunita").contains("person" to "User's mother is Sunita"))
    @Test fun learnsPreference() = assertTrue(cats("i love cricket").any { it.first == "preference" && it.second.contains("cricket") })
    @Test fun ignoresQuestions() = assertTrue(cats("what is my name?").isEmpty())
    @Test fun ignoresSmallTalk() = assertTrue(cats("open youtube").isEmpty())
}
