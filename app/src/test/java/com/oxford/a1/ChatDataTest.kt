package com.oxford.a1

import android.app.Application
import com.oxford.a1.ai.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChatDataTest {
    private fun decoded(messages: Array<ByteArray>) = messages.map { it.toString(Charsets.UTF_8) }
    @Test fun shortContextRetainsOnlyTwoCompleteRoundsInRoleOrder() {
        val history = (1..4).map { ChatTurn("question$it", "answer$it", ReplyStatus.COMPLETE) }
        assertEquals(listOf("question3", "answer3", "question4", "answer4", "你好"), decoded(ChatContext.messages(history, "你好")))
    }
    @Test fun interruptedFailedAndEmptyRepliesAreNotUsedAsContext() {
        val history = listOf(ChatTurn("good", "answer", ReplyStatus.COMPLETE),
            ChatTurn("cancelled", "partial", ReplyStatus.INTERRUPTED), ChatTurn("error", "partial", ReplyStatus.FAILED),
            ChatTurn("empty", "", ReplyStatus.COMPLETE), ChatTurn("pending"))
        assertEquals(listOf("good", "answer", "next"), decoded(ChatContext.messages(history, "next")))
    }
    @Test fun inputBoundsRejectEmptyOversizedAndNullCharacters() {
        for (input in listOf(" ", "a".repeat(801), "hello\u0000there")) {
            assertThrows(IllegalArgumentException::class.java) { ChatContext.messages(emptyList(), input) }
        }
        assertEquals(1, ChatContext.messages(emptyList(), "a".repeat(800)).size)
    }
    @Test fun skillsPersistEditSelectAndDeleteWithoutTouchingOtherFiles() {
        val app = RuntimeEnvironment.getApplication()
        val sentinel = java.io.File(app.filesDir, "learning-sentinel").apply { writeText("keep") }
        val store = SkillStore(app)
        val custom = SkillStore.create("口语教练", "用简单英语提问，每次只问一个问题。")
        store.save(SkillSettings(listOf(custom), custom.id))
        assertEquals(custom, SkillStore(app).read().selected)
        val edited = custom.copy(instruction = "先纠正语法，再用中文解释。")
        store.save(SkillSettings(listOf(edited), edited.id))
        assertEquals(edited, SkillStore(app).read().selected)
        store.save(SkillSettings())
        assertEquals(SkillStore.DEFAULT, SkillStore(app).read().selected)
        assertEquals("keep", sentinel.readText())
    }
    @Test fun invalidSkillSavePreservesPreviousSettings() {
        val store = SkillStore(RuntimeEnvironment.getApplication())
        val skill = SkillStore.create("有效", "Keep it simple.")
        store.save(SkillSettings(listOf(skill), skill.id))
        for (invalid in listOf(skill.copy(name = ""), skill.copy(instruction = "x".repeat(1201)), skill.copy(id = "default"))) {
            assertThrows(IllegalArgumentException::class.java) { store.save(SkillSettings(listOf(invalid), invalid.id)) }
        }
        assertEquals(skill, store.read().selected)
    }
    @Test fun missingSelectionFallsBackToBuiltInAndSkillCountIsBounded() {
        assertEquals(SkillStore.DEFAULT, SkillSettings(selectedId = "deleted").selected)
        val skills = (1..21).map { SkillStore.create("s$it", "prompt") }
        assertThrows(IllegalArgumentException::class.java) { SkillStore.validate(skills) }
    }
}
