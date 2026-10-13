package com.oxford.a1.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oxford.a1.data.model.*
import com.oxford.a1.data.repository.WordRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WordUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val allWords: List<Word> = emptyList(),
    val visibleWords: List<Word> = emptyList(),
    val filter: WordFilter = WordFilter(),
    val selectedWord: Word? = null
)

class WordViewModel(private val repository: WordRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val selectedId = saved.getStateFlow<Long?>("selected", null)
    private val filter = combine(
        saved.getStateFlow("query", ""),
        saved.getStateFlow<String?>("pos", null),
        saved.getStateFlow("learned", LearnedFilter.ALL.name),
        saved.getStateFlow("ascending", true),
        saved.getStateFlow<String?>("level", null)
    ) { query, pos, learned, ascending, level -> WordFilter(query, pos, LearnedFilter.valueOf(learned), ascending, level) }

    private val words = repository.words.catch { exception ->
        if (exception is CancellationException) throw exception
        error.value = "无法读取本地词库，请重新打开应用。"
        emit(emptyList())
    }

    val state = combine(words, filter, selectedId, loading, error) { words, filter, id, loading, error ->
        val detail = filterWords(words.filter { it.id == id }, WordFilter(partOfSpeech = filter.partOfSpeech, cefrLevel = filter.cefrLevel))
            .find { it.id == id }
        WordUiState(loading || (words.isEmpty() && error == null), error, words, filterWords(words, filter), filter, detail)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordUiState())

    init { initialize() }

    fun initialize() {
        if (!loading.value && error.value == null) return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                repository.initialize()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error.value = "词库导入失败，请重试。${exception.localizedMessage.orEmpty()}"
            } finally {
                loading.value = false
            }
        }
    }

    fun setQuery(value: String) { saved["query"] = value }
    fun setPartOfSpeech(value: String?) { saved["pos"] = value }
    fun setLevel(value: String?) { saved["level"] = value }
    fun setLearnedFilter(value: LearnedFilter) { saved["learned"] = value.name }
    fun toggleSort() { saved["ascending"] = !state.value.filter.ascending }
    fun selectWord(id: Long?) { saved["selected"] = id }
    fun resetFilters() {
        saved["query"] = ""
        saved["pos"] = null
        saved["level"] = null
        saved["learned"] = LearnedFilter.ALL.name
    }

    fun toggleLearned(id: Long) {
        viewModelScope.launch {
            try { repository.toggleLearned(id) }
            catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error.value = "掌握状态保存失败，请重试。"
            }
        }
    }
    fun confirmLearned(id: Long) {
        viewModelScope.launch {
            try { repository.confirmLearned(id) }
            catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                error.value = "掌握状态保存失败，请重试。"
            }
        }
    }
}
