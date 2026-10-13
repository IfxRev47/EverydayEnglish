package com.oxford.a1.ai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oxford.a1.data.model.Word
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AiState(val modelName: String? = null, val loaded: Boolean = false, val busy: Boolean = false,
    val status: String = "请导入单文件 GGUF 指令模型", val output: String = "", val targets: List<String> = emptyList(),
    val skills: SkillSettings = SkillSettings(), val turns: List<ChatTurn> = emptyList(),
    val settings: AiSettings = AiSettings(), val practiceWordId: Long? = null,
    val practiceReady: Boolean = false, val recommendedIds: List<Long> = emptyList(), val recommendationLevel: String? = null)
data class AiSpeechRequest(val text: String, val fromChat: Boolean)

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ModelStore(application)
    private val skillStore = SkillStore(application)
    private val settingsStore = AiSettingsStore(application)
    private val savedSkills = runCatching { skillStore.read() }
    private val mutable = MutableStateFlow(AiState(modelName = store.name(), settings = settingsStore.read(),
        skills = savedSkills.getOrDefault(SkillSettings()),
        status = if (savedSkills.isFailure) "Skill 文件读取失败，暂用内置英语陪练；原文件未改动" else "请导入单文件 GGUF 指令模型"))
    val state = mutable.asStateFlow()
    private val speechEvents = MutableSharedFlow<AiSpeechRequest>(extraBufferCapacity = 1)
    val speechRequests = speechEvents.asSharedFlow()
    private var job: Job? = null
    @Volatile private var nativeReady = false
    private val identity = Any()
    // Global native session: cleanup from a destroyed Activity cannot overlap a new operation.
    companion object {
        private val gate = Mutex()
        @Volatile private var engineOwner: Any? = null
    }
    private fun runOperation(status: String, block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, status = status) }
        job = viewModelScope.launch {
            try { withContext(Dispatchers.IO) { gate.withLock { block() } } }
            catch (_: CancellationException) { mutable.update { it.copy(status = "已停止") } }
            catch (e: Exception) { failChat(message(e)) }
            catch (_: LinkageError) { failChat("本机无法加载 ARM64 推理库，请使用 64 位 ARM Android 设备") }
            finally { mutable.update { it.copy(busy = false, modelName = store.name(),
                turns = it.turns.map { turn -> if (turn.status == ReplyStatus.PENDING) turn.copy(status = ReplyStatus.INTERRUPTED) else turn }) } }
        }
    }
    private fun prepare() { LlamaNative.reset(); nativeReady = true }
    private fun releaseOwnedEngine() {
        if (nativeReady && engineOwner === identity) {
            LlamaNative.unload()
            engineOwner = null
        }
    }
    fun import(uri: Uri) = runOperation("正在复制模型…") {
        releaseOwnedEngine()
        mutable.update { it.copy(loaded = false) }
        store.import(uri) { size -> mutable.update { it.copy(status = "正在导入：${size / (1024 * 1024)} MiB") } }
        mutable.update { it.copy(status = "导入完成，点击加载模型") }
    }
    fun load() = runOperation("正在加载模型，首次可能较慢…") {
        mutable.update { it.copy(loaded = false) }
        engineOwner = identity
        try {
            prepare()
            currentCoroutineContext().ensureActive()
            val settings = state.value.settings
            LlamaNative.load(store.model.absolutePath.toByteArray(Charsets.UTF_8), settings.threads, settings.contextSize)
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(loaded = true, status = "模型已加载，可离线聊天或造句") }
        } catch (e: Throwable) { releaseOwnedEngine(); throw e }
    }
    fun generate(words: List<Word>) {
        if (!state.value.loaded || state.value.busy) return
        val targets = LearningPrompt.targets(words)
        if (targets.isEmpty()) { mutable.update { it.copy(status = "请先在词库标记已掌握的单词") }; return }
        mutable.update { it.copy(output = "", targets = targets, practiceWordId = null, practiceReady = false) }
        runOperation("正在造句…") {
            if (engineOwner !== identity) {
                mutable.update { it.copy(loaded = false) }
                error("NOT_LOADED")
            }
            prepare()
            currentCoroutineContext().ensureActive()
            val settings = state.value.settings
            LlamaNative.generate(arrayOf(LearningPrompt.build(targets).toByteArray(Charsets.UTF_8)), byteArrayOf(),
                settings.temperature, settings.topP, settings.topK, settings.repeatPenalty, settings.maxTokens, TextListener { bytes ->
                // Re-decode the accumulated bytes, hiding any unfinished UTF-8 character at the tail.
                mutable.update { it.copy(output = bytes.toString(Charsets.UTF_8).trimEnd('\uFFFD')) }
            })
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(status = "生成结束（最多 ${settings.maxTokens} token）；可再次生成练习") }
            if (settings.autoSpeak) speechEvents.tryEmit(AiSpeechRequest(state.value.output, false))
        }
    }
    fun generatePractice(word: Word): Boolean {
        if (!state.value.loaded || state.value.busy) return false
        mutable.update { it.copy(output = "", targets = listOf(word.word), practiceWordId = word.id, practiceReady = false) }
        runOperation("正在生成简单练习句…") {
            if (engineOwner !== identity) { mutable.update { it.copy(loaded = false) }; error("NOT_LOADED") }
            prepare()
            currentCoroutineContext().ensureActive()
            val settings = state.value.settings
            LlamaNative.generate(arrayOf(com.oxford.a1.practice.PracticePrompt.build(word.word).toByteArray(Charsets.UTF_8)), byteArrayOf(),
                settings.temperature, settings.topP, settings.topK, settings.repeatPenalty, minOf(settings.maxTokens, 128),
                TextListener { bytes -> mutable.update { it.copy(output = bytes.toString(Charsets.UTF_8).trimEnd('\uFFFD')) } })
            currentCoroutineContext().ensureActive()
            val sentence = com.oxford.a1.practice.PracticePrompt.sentence(state.value.output, word.word)
            mutable.update { it.copy(output = sentence.orEmpty(), practiceReady = sentence != null,
                status = if (sentence != null) "练习句已准备好" else "模型未生成可用的简单句，请重试或换一个词") }
            // Dictation answers must never be auto-spoken before the learner presses play.
        }
        return true
    }
    fun clearPractice() {
        if (!state.value.busy) mutable.update { it.copy(output = "", practiceWordId = null, practiceReady = false) }
    }
    fun recommend(words: List<Word>, level: String, count: Int) {
        if (!state.value.loaded || state.value.busy) return
        require(count in 1..30)
        val candidates = com.oxford.a1.practice.Recommendation.candidates(words, level)
        mutable.update { it.copy(recommendedIds = emptyList(), recommendationLevel = level) }
        if (candidates.isEmpty()) { mutable.update { it.copy(status = "该等级没有未掌握候选词，可以手动选择") }; return }
        runOperation("正在从 $level 未掌握词中推荐…") {
            if (engineOwner !== identity) { mutable.update { it.copy(loaded = false) }; error("NOT_LOADED") }
            prepare()
            currentCoroutineContext().ensureActive()
            val settings = state.value.settings
            var output = ""
            LlamaNative.generate(arrayOf(com.oxford.a1.practice.Recommendation.prompt(candidates, count).toByteArray(Charsets.UTF_8)), byteArrayOf(),
                settings.temperature, settings.topP, settings.topK, settings.repeatPenalty, minOf(settings.maxTokens, 256),
                TextListener { bytes -> output = bytes.toString(Charsets.UTF_8).trimEnd('\uFFFD') })
            currentCoroutineContext().ensureActive()
            val ids = com.oxford.a1.practice.Recommendation.parse(output, candidates, count)
            mutable.update { it.copy(recommendedIds = ids, status = if (ids.isEmpty()) "没有得到有效推荐，请重试或手动选词" else "已推荐 ${ids.size} 个词，确认后加入今日目标") }
        }
    }
    private fun failChat(message: String) {
        mutable.update { it.copy(status = message, turns = it.turns.map { turn ->
            if (turn.status == ReplyStatus.PENDING) turn.copy(status = ReplyStatus.FAILED) else turn
        }) }
    }
    fun sendChat(text: String): Boolean {
        if (state.value.busy || !state.value.loaded) return false
        val input = text.trim()
        val settings = state.value.settings
        val messages = try { ChatContext.messages(state.value.turns, input, settings.historyRounds) }
        catch (e: IllegalArgumentException) { failChat(e.message.orEmpty()); return false }
        val skill = state.value.skills.selected.instruction.toByteArray(Charsets.UTF_8)
        mutable.update { it.copy(turns = (it.turns + ChatTurn(input)).takeLast(20)) }
        runOperation("正在回复…") {
            if (engineOwner !== identity) {
                mutable.update { it.copy(loaded = false) }
                error("NOT_LOADED")
            }
            prepare()
            currentCoroutineContext().ensureActive()
            LlamaNative.generate(messages, skill, settings.temperature, settings.topP, settings.topK,
                settings.repeatPenalty, settings.maxTokens, TextListener { bytes ->
                mutable.update { current -> current.copy(turns = current.turns.dropLast(1) +
                    current.turns.last().copy(assistant = bytes.toString(Charsets.UTF_8).trimEnd('\uFFFD'))) }
            })
            currentCoroutineContext().ensureActive()
            mutable.update { it.copy(status = "回复结束（最多 ${settings.maxTokens} token）", turns = it.turns.dropLast(1) +
                it.turns.last().copy(status = ReplyStatus.COMPLETE)) }
            if (settings.autoSpeak) speechEvents.tryEmit(AiSpeechRequest(state.value.turns.last().assistant, true))
        }
        return true
    }
    fun clearChat() {
        if (!state.value.busy) mutable.update { it.copy(turns = emptyList(), status = "新对话已开始") }
    }
    fun saveSettings(settings: AiSettings) {
        if (state.value.busy) return
        runOperation("正在保存参数…") {
            settings.validate()
            settingsStore.save(settings)
            val reload = state.value.settings.requiresReload(settings)
            if (reload) releaseOwnedEngine()
            mutable.update { it.copy(settings = settings, loaded = it.loaded && !reload,
                status = if (reload) "参数已保存，请重新加载模型" else "参数已保存，下次生成使用新参数") }
        }
    }
    private fun commitSkills(settings: SkillSettings): Boolean {
        if (state.value.busy) return false
        return try {
            skillStore.save(settings)
            mutable.update { it.copy(skills = settings, turns = emptyList(), status = "Skill 已保存，新对话已开始") }
            true
        } catch (e: Exception) { failChat("Skill 保存失败：${e.message}"); false }
    }
    fun selectSkill(id: String) = commitSkills(state.value.skills.copy(selectedId = id))
    fun saveSkill(id: String?, name: String, instruction: String): Boolean {
        val skill = if (id == null) SkillStore.create(name, instruction) else ChatSkill(id, name.trim(), instruction.trim())
        val skills = state.value.skills.skills.filterNot { it.id == skill.id } + skill
        return commitSkills(SkillSettings(skills, skill.id))
    }
    fun deleteSkill(id: String): Boolean = commitSkills(SkillSettings(state.value.skills.skills.filterNot { it.id == id }))
    fun stop() {
        job?.cancel()
        if (nativeReady && engineOwner === identity) runCatching { LlamaNative.cancel() }
    }
    fun unload(delete: Boolean = false) = runOperation("正在释放模型…") {
        releaseOwnedEngine()
        if (delete) store.delete()
        mutable.update { it.copy(loaded = false, status = if (delete) "模型已删除" else "模型已释放，文件仍保留") }
    }
    override fun onCleared() {
        stop()
        // Native memory cleanup must outlive the cancelled ViewModel scope.
        CoroutineScope(Dispatchers.IO).launch { gate.withLock { releaseOwnedEngine() } }
        super.onCleared()
    }
    private fun message(e: Exception): String = when (e.message) {
        "NOT_LOADED" -> "模型已被释放，请重新加载"
        "TEMPLATE_UNSUPPORTED" -> "此模型的对话模板不受支持，请导入带受支持模板的 Instruct/Chat GGUF 模型"
        "LOAD_FAILED", "CONTEXT_FAILED" -> "加载失败：请检查模型是否完整、架构是否受支持以及可用内存；尝试更小的量化模型"
        "PROMPT_TOO_LONG" -> "已移除旧对话，输入仍超过上下文限制；请缩短消息或 Skill 内容"
        "DECODE_FAILED" -> "推理失败，请释放后重新加载模型"
        "INVALID_SETTINGS" -> "参数不在支持范围内，请恢复默认设置"
        else -> "操作失败：${e.message ?: e.javaClass.simpleName}"
    }
}
