package com.oxford.a1

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.oxford.a1.ai.*
import com.oxford.a1.audio.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import org.robolectric.shadows.ShadowLooper
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AiSettingsSpeechTest {
    @Test fun parametersPersistAndInvalidUpdateDoesNotReplaceThem() {
        val app = RuntimeEnvironment.getApplication()
        val store = AiSettingsStore(app)
        val selected = AiSettings(temperature = 0f, topP = 0.8f, topK = 20, repeatPenalty = 1.2f,
            contextSize = 1024, maxTokens = 128, threads = 2, historyRounds = 0, autoSpeak = true, speechRate = 0.7f, speechPitch = 0.9f)
        store.save(selected)
        assertEquals(selected, AiSettingsStore(app).read())
        for (invalid in listOf(selected.copy(temperature = Float.NaN), selected.copy(maxTokens = 900),
            selected.copy(contextSize = 8192), selected.copy(threads = 0), selected.copy(speechRate = 3f))) {
            assertThrows(IllegalArgumentException::class.java) { store.save(invalid) }
        }
        assertEquals(selected, store.read())
    }
    @Test fun onlyContextAndThreadsRequireModelReload() {
        val d = AiSettings()
        assertFalse(d.requiresReload(d.copy(temperature = 0f, maxTokens = 128, autoSpeak = true)))
        assertTrue(d.requiresReload(d.copy(contextSize = 4096)))
        assertTrue(d.requiresReload(d.copy(threads = if (d.threads == 1) 2 else 1)))
    }
    @Test fun configurableHistoryCanBeDisabledOrLimitedToOneRound() {
        val history = (1..2).map { ChatTurn("q$it", "a$it", ReplyStatus.COMPLETE) }
        assertEquals(1, ChatContext.messages(history, "next", 0).size)
        assertEquals(listOf("q2", "a2", "next"), ChatContext.messages(history, "next", 1).map { it.toString(Charsets.UTF_8) })
    }
    @Test fun speechSkipsChineseCodeAndReasoningAndChunksLongEnglish() {
        val text = "<think>Hidden thought</think>\n1. I like apples.\n我喜欢苹果。\n```kotlin\nsecret code\n```\nHow are you?"
        assertEquals("I like apples. How are you?", SpeechText.chunks(text).joinToString(" "))
        assertTrue(SpeechText.chunks("只有中文。").isEmpty())
        assertTrue(SpeechText.chunks("<think>unfinished reasoning").isEmpty())
        val longText = "Hello world. ".repeat(600)
        val parts = SpeechText.chunks(longText, 1000)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 1000 })
        assertEquals(longText.trim(), parts.joinToString(" "))
    }
    private fun provider(): TtsAudioProvider {
        ShadowTextToSpeech.addVoice(Voice("offline-en", Locale.UK, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, emptySet()))
        val provider = TtsAudioProvider(RuntimeEnvironment.getApplication())
        shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).onInitListener.onInit(TextToSpeech.SUCCESS)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertTrue(provider.state.value.ready)
        return provider
    }
    @Test fun englishReplyUsesOfflineVoiceAndDrainsAllChunks() {
        val provider = provider()
        try {
            val text = "I like apples. ".repeat(150) + "我喜欢苹果。"
            provider.speakEnglish(text, 0.7f, 1f)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
            assertEquals(SpeechText.chunks(text), shadow.spokenTextList.toList())
            assertFalse(shadow.currentVoice.isNetworkConnectionRequired)
            assertFalse(provider.state.value.speakingText)
        } finally { provider.close() }
    }
    @Test fun stopDiscardsPendingChunksAndIgnoresLateCompletion() {
        val provider = provider()
        try {
            provider.speakEnglish("Hello world. ".repeat(300))
            provider.stop()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
            assertEquals(1, shadow.spokenTextList.size)
            assertFalse(provider.state.value.speakingText)
        } finally { provider.close() }
    }
}
