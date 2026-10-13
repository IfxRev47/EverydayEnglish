package com.oxford.a1.grammar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GrammarState(val loading: Boolean = false, val error: String? = null, val lessons: List<GrammarLesson> = emptyList(),
    val progress: Map<String, GrammarProgress> = emptyMap(), val lastId: String? = null, val lessonId: String? = null,
    val step: Int = 0, val bank: List<String> = emptyList(), val selected: List<Int> = emptyList(),
    val guidedCorrect: Boolean? = null, val independent: String = "", val independentCorrect: Boolean? = null,
    val own: String = "", val spoken: Boolean = false, val saved: Boolean = false) {
    val lesson: GrammarLesson? get() = lessons.find { it.id == lessonId }
    val assembled: String get() = selected.joinToString(" ") { bank[it] }
}

class GrammarViewModel(application: Application) : AndroidViewModel(application) {
    private val store = GrammarProgressStore(application)
    private val mutable = MutableStateFlow(GrammarState())
    val state = mutable.asStateFlow()
    fun open() {
        if (state.value.lessons.isNotEmpty() || state.value.loading) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val lessons = withContext(Dispatchers.IO) {
                    getApplication<Application>().assets.open("grammar_beginner.json").bufferedReader().use { GrammarContent.parse(it.readText()) }
                }
                mutable.update { it.copy(loading = false, lessons = lessons,
                    progress = lessons.associate { lesson -> lesson.id to store.read(lesson.id) }, lastId = store.lastLesson()) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(loading = false, error = "课程读取失败，请重试：${e.message}") }
            }
        }
    }
    fun select(id: String, restart: Boolean = false) {
        val lesson = state.value.lessons.find { it.id == id } ?: return
        val progress = store.read(id)
        val step = if (restart) 0 else progress.step
        store.step(id, step)
        mutable.update { it.copy(lessonId = id, step = step, bank = lesson.guided.tokens.shuffled(), selected = emptyList(),
            guidedCorrect = null, independent = "", independentCorrect = null, own = progress.ownSentence,
            spoken = false, saved = false, lastId = id, progress = it.progress + (id to store.read(id))) }
    }
    fun back() { mutable.update { it.copy(lessonId = null) } }
    fun advance() {
        val current = state.value
        val lesson = current.lesson ?: return
        if (current.step == 3 && current.guidedCorrect != true) return
        val next = (current.step + 1).coerceAtMost(4)
        store.step(lesson.id, next)
        mutable.update { it.copy(step = next, progress = it.progress + (lesson.id to store.read(lesson.id))) }
    }
    fun previous() {
        val lesson = state.value.lesson ?: return
        val previous = (state.value.step - 1).coerceAtLeast(0)
        store.step(lesson.id, previous)
        mutable.update { it.copy(step = previous, progress = it.progress + (lesson.id to store.read(lesson.id))) }
    }
    fun token(index: Int) {
        if (index !in state.value.bank.indices || index in state.value.selected) return
        mutable.update { it.copy(selected = it.selected + index, guidedCorrect = null) }
    }
    fun undo() { mutable.update { it.copy(selected = it.selected.dropLast(1), guidedCorrect = null) } }
    fun checkGuided() {
        val current = state.value
        val lesson = current.lesson ?: return
        mutable.update { it.copy(guidedCorrect = current.selected.size == current.bank.size && matchesGrammar(current.assembled, lesson.guided.answers)) }
    }
    fun independent(value: String) { mutable.update { it.copy(independent = value.take(300), independentCorrect = null, saved = false) } }
    fun checkIndependent() {
        val lesson = state.value.lesson ?: return
        mutable.update { it.copy(independentCorrect = matchesGrammar(it.independent, lesson.independent.answers)) }
    }
    fun own(value: String) { mutable.update { it.copy(own = value.take(300), saved = false) } }
    fun spoken(value: Boolean) { mutable.update { it.copy(spoken = value, saved = false) } }
    fun complete() {
        val current = state.value
        val lesson = current.lesson ?: return
        if (current.step != 4 || current.independentCorrect != true || !current.spoken || current.own.none { it in 'a'..'z' || it in 'A'..'Z' }) return
        store.complete(lesson.id, current.own)
        mutable.update { it.copy(saved = true, progress = it.progress + (lesson.id to store.read(lesson.id))) }
    }
}
