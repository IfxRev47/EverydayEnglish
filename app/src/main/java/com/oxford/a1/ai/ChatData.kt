package com.oxford.a1.ai

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

data class ChatSkill(val id: String, val name: String, val instruction: String)
data class SkillSettings(val skills: List<ChatSkill> = emptyList(), val selectedId: String = "default") {
    val selected: ChatSkill get() = skills.find { it.id == selectedId } ?: SkillStore.DEFAULT
}

/** Skills are local prompt text, not executable scripts or agent plugins. */
class SkillStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "chat-skills.json"))
    fun read(): SkillSettings {
        // openRead also restores AtomicFile's backup after an interrupted write.
        val content = try { file.openRead().bufferedReader().use { it.readText() } }
        catch (e: FileNotFoundException) {
            if (file.baseFile.exists()) throw e
            return SkillSettings()
        }
        val json = JSONObject(content)
        val items = json.getJSONArray("skills")
        val skills = (0 until items.length()).map { i -> items.getJSONObject(i).let {
            ChatSkill(it.getString("id"), it.getString("name"), it.getString("instruction"))
        } }
        validate(skills)
        return SkillSettings(skills, json.optString("selectedId", "default"))
    }
    fun save(settings: SkillSettings) {
        validate(settings.skills)
        val items = JSONArray()
        settings.skills.forEach { items.put(JSONObject().put("id", it.id).put("name", it.name).put("instruction", it.instruction)) }
        val bytes = JSONObject().put("skills", items).put("selectedId", settings.selected.id).toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
    companion object {
        const val MAX_SKILLS = 20
        const val MAX_INSTRUCTION = 1200
        val DEFAULT = ChatSkill("default", "英语陪练", "You are a friendly English tutor for a beginner. Use short A1/A2 English sentences. Explain difficult words briefly in Simplified Chinese. Gently correct mistakes and ask one simple follow-up question. Keep answers concise.")
        fun create(name: String, instruction: String) = ChatSkill(UUID.randomUUID().toString(), name.trim(), instruction.trim())
        fun validate(skills: List<ChatSkill>) {
            require(skills.size <= MAX_SKILLS) { "最多保存 20 个自定义 Skill" }
            require(skills.map { it.id }.distinct().size == skills.size)
            skills.forEach {
                require(it.id.isNotBlank() && it.id != "default")
                require(it.name.isNotBlank() && it.name.length <= 30) { "名称需要 1～30 个字符" }
                require(it.instruction.isNotBlank() && it.instruction.length <= MAX_INSTRUCTION && '\u0000' !in it.instruction) { "Skill 内容需要 1～1200 个字符，不能包含空字符" }
            }
        }
    }
}

enum class ReplyStatus { PENDING, COMPLETE, INTERRUPTED, FAILED }
data class ChatTurn(val user: String, val assistant: String = "", val status: ReplyStatus = ReplyStatus.PENDING)
object ChatContext {
    const val MAX_INPUT = 800
    const val HISTORY_ROUNDS = 2
    fun messages(turns: List<ChatTurn>, input: String, rounds: Int = HISTORY_ROUNDS): Array<ByteArray> {
        require(rounds in 0..HISTORY_ROUNDS)
        require(input.isNotBlank() && input.length <= MAX_INPUT && '\u0000' !in input) { "消息需要 1～800 个字符，不能包含空字符" }
        val history = turns.filter { it.status == ReplyStatus.COMPLETE && it.assistant.isNotBlank() }.takeLast(rounds)
        return (history.flatMap { listOf(it.user, it.assistant) } + input).map { it.toByteArray(Charsets.UTF_8) }.toTypedArray()
    }
}
