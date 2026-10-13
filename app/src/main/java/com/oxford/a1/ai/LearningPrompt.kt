package com.oxford.a1.ai

import com.oxford.a1.data.model.Word

object LearningPrompt {
    fun targets(words: List<Word>): List<String> = words.filter { it.learned }.map { it.word }.distinct().shuffled().take(5)
    fun build(targets: List<String>): String {
        require(targets.isNotEmpty()) { "请先标记几个已掌握的单词" }
        return """
            /no_think
            You are an English tutor for a beginner. Write exactly 3 short, natural English sentences (5-12 words each).
            Practise these words the learner already knows: ${targets.joinToString(", ")}.
            Use all these target words across the sentences when natural. Inflections and basic function words are allowed.
            Prefer simple A1/A2 vocabulary. After each sentence give a concise Simplified Chinese translation.
            Format: 1. English sentence on one line, Chinese translation on the next line.
            Output only the three numbered sentences and translations. Do not explain or show reasoning.
        """.trimIndent()
    }
}
