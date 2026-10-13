package com.oxford.a1.data.model

data class Word(
    val id: Long,
    val word: String,
    val phonetic: String,
    val learned: Boolean,
    val meanings: List<Meaning>
)

data class Meaning(val partOfSpeech: String, val chineseMeaning: String, val cefrLevel: String,
                   val phonetic: String = "", val headword: String = "")

enum class LearnedFilter(val label: String) {
    ALL("全部"), UNLEARNED("未掌握"), LEARNED("已掌握")
}

data class WordFilter(
    val query: String = "",
    val partOfSpeech: String? = null,
    val learned: LearnedFilter = LearnedFilter.ALL,
    val ascending: Boolean = true,
    val cefrLevel: String? = null
)

fun filterWords(words: List<Word>, filter: WordFilter): List<Word> {
    val query = filter.query.trim()
    val result = words.mapNotNull { original ->
        val meanings = original.meanings.filter {
            (filter.cefrLevel == null || it.cefrLevel == filter.cefrLevel) &&
                (filter.partOfSpeech == null || it.partOfSpeech == filter.partOfSpeech)
        }
        if (meanings.isEmpty()) null else original.copy(
            word = meanings.first().headword.ifBlank { original.word },
            phonetic = meanings.map { it.phonetic.ifBlank { original.phonetic } }.distinct().joinToString("; "),
            meanings = meanings
        )
    }.filter { word ->
        (filter.learned == LearnedFilter.ALL || word.learned == (filter.learned == LearnedFilter.LEARNED)) &&
            (filter.partOfSpeech == null || word.meanings.any { it.partOfSpeech == filter.partOfSpeech }) &&
            (query.isEmpty() || word.word.contains(query, ignoreCase = true) ||
                word.meanings.any { it.chineseMeaning.contains(query) })
    }.sortedBy { it.word.lowercase(java.util.Locale.ROOT) }
    return if (filter.ascending) result else result.asReversed()
}

fun posLabel(pos: String): String = when (pos) {
    "noun" -> "n. 名词"
    "verb" -> "v. 动词"
    "linking verb" -> "link v. 系动词"
    "adjective" -> "adj. 形容词"
    "adverb" -> "adv. 副词"
    "preposition" -> "prep. 介词"
    "pronoun" -> "pron. 代词"
    "determiner" -> "det. 限定词"
    "auxiliary verb" -> "aux. 助动词"
    "modal verb" -> "modal 情态动词"
    "conjunction" -> "conj. 连词"
    "exclamation" -> "感叹词"
    "number" -> "基数词"
    "ordinal number" -> "序数词"
    "indefinite article" -> "不定冠词"
    "definite article" -> "定冠词"
    "infinitive marker" -> "不定式标记"
    else -> pos
}
