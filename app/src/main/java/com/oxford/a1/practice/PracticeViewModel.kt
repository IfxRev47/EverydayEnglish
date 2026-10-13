package com.oxford.a1.practice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.oxford.a1.data.model.Word
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PracticeMode(val title: String, val kind: StudyKind?) {
    MENU("今日背诵计划", null), TODAY("今日单词背诵", null), REVIEW("已掌握随机回顾", StudyKind.REVIEW),
    WORD_DICTATION("单词听写", StudyKind.DICTATION), AI_DICTATION("AI 句子听写", StudyKind.DICTATION),
    SENTENCE("AI 造句练习", StudyKind.SENTENCE)
}
data class PracticeState(val mode: PracticeMode = PracticeMode.MENU, val ids: List<Long> = emptyList(),
    val index: Int = 0, val answer: String = "", val revealed: Boolean = false,
    val submitted: Boolean = false, val correct: Boolean = false, val completed: Boolean = false,
    val custom: Boolean = false) {
    val currentId: Long? get() = ids.getOrNull(index)
}

class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = StudyProgressStore(application)
    private val mutableDaily = MutableStateFlow(store.read())
    val daily = mutableDaily.asStateFlow()
    private val mutableHistory = MutableStateFlow(store.history())
    val history = mutableHistory.asStateFlow()
    private val mutable = MutableStateFlow(PracticeState())
    val state = mutable.asStateFlow()
    fun refresh() {
        val progress = store.read()
        if (progress.date != daily.value.date) mutable.value = PracticeState()
        mutableDaily.value = progress
        mutableHistory.value = store.history()
    }
    fun goals(values: Map<StudyKind, Int>) { mutableDaily.value = store.saveGoals(values) }
    fun select(ids: Set<Long>, dictation: Boolean = false) { mutableDaily.value = store.select(ids, dictation) }
    fun addRecommended(ids: List<Long>) { refresh(); select((daily.value.planned + ids).take(200).toSet()) }
    fun start(words: List<Word>, mode: PracticeMode, level: String?, custom: Boolean = false) {
        refresh()
        val ids = if (mode == PracticeMode.REVIEW) selectReviewWords(words, level).map { it.id }
            else words.filter { it.id in (if (custom) daily.value.dictation else daily.value.planned) }
                .sortedBy { it.word.lowercase(java.util.Locale.ROOT) }.map { it.id }
        mutable.value = PracticeState(mode, ids = if (mode == PracticeMode.TODAY) ids else ids.shuffled(), custom = custom)
    }
    fun menu() { mutable.value = PracticeState(); refresh() }
    fun answer(text: String) { if (!state.value.submitted) mutable.value = state.value.copy(answer = text.take(500)) }
    fun reveal() { mutable.value = state.value.copy(revealed = true) }
    fun submit(expected: String) {
        if (state.value.submitted || state.value.answer.isBlank() || expected.isBlank()) return
        mutable.value = state.value.copy(submitted = true, revealed = true,
            correct = normalizeAnswer(state.value.answer) == normalizeAnswer(expected))
    }
    fun finish(word: Word) {
        val current = state.value
        if (current.completed || current.currentId != word.id) return
        val allowed = when (current.mode) {
            PracticeMode.REVIEW, PracticeMode.TODAY -> current.revealed
            PracticeMode.WORD_DICTATION, PracticeMode.AI_DICTATION -> current.submitted
            PracticeMode.SENTENCE -> true
            PracticeMode.MENU -> false
        }
        if (!allowed) return
        mutableDaily.value = if (current.mode == PracticeMode.TODAY) store.memorize(word.id) else store.record(current.mode.kind!!, word.word)
        mutableHistory.value = store.history()
        mutable.value = current.copy(completed = true)
    }
    fun next() {
        val current = state.value
        mutable.value = current.copy(index = current.index + 1, answer = "", revealed = false,
            submitted = false, correct = false, completed = false)
    }
}

fun selectReviewWords(words: List<Word>, level: String?, limit: Int = 10): List<Word> = words
    .filter { it.learned && (level == null || it.meanings.any { meaning -> meaning.cefrLevel == level }) }
    .distinctBy { it.id }.shuffled().take(limit)

fun normalizeAnswer(text: String): String = text.lowercase(java.util.Locale.ROOT).replace('’', '\'')
    .replace(Regex("[^a-z0-9']+"), " ").trim().replace(Regex("\\s+"), " ")
