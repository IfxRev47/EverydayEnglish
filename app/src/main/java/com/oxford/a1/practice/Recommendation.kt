package com.oxford.a1.practice

import com.oxford.a1.data.model.Word
import java.util.Locale

object Recommendation {
    fun candidates(words: List<Word>, level: String): List<Word> = words.filter {
        !it.learned && it.meanings.any { meaning -> meaning.cefrLevel == level }
    }.distinctBy { it.id }.shuffled().take(60)

    fun prompt(words: List<Word>, count: Int): String = """
        /no_think
        Choose up to $count useful everyday words for a beginner to study today ONLY from this candidate list.
        Prefer related words that can be used in simple daily conversations. Do not invent or change any words.
        Output ONLY the exact chosen words, one per line, with no numbers, explanation, translation or reasoning.
        Candidates:
        ${words.joinToString("\n") { it.word }}
    """.trimIndent()

    fun parse(output: String, candidates: List<Word>, count: Int): List<Long> {
        val allowed = candidates.associateBy { it.word.lowercase(Locale.ROOT) }
        return output.replace(Regex("(?is)<think>.*?(</think>|$)"), "")
            .split('\n', ',', ';').mapNotNull { line ->
                val key = line.trim().replace(Regex("^[-*\\d.)]+\\s*"), "").trim('"', '\'', '`', ' ').lowercase(Locale.ROOT)
                allowed[key]?.id
            }.distinct().take(count)
    }
}
