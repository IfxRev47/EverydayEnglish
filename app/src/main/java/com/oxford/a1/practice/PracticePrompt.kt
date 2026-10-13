package com.oxford.a1.practice

object PracticePrompt {
    fun build(word: String): String = """
        /no_think
        Write exactly ONE natural English sentence for an A1 beginner containing the exact word or phrase "$word".
        Use 3 to 12 words, simple present tense and common everyday words. Avoid names and abbreviations.
        Output ONLY the English sentence. No title, number, translation, explanation, quotes or reasoning.
    """.trimIndent()

    /** Reject extra instructions or unusable output before making a dictation answer. */
    fun sentence(output: String, target: String): String? {
        val clean = output.replace(Regex("(?is)<think>.*?(</think>|$)"), "")
        val line = clean.trim().lines().firstOrNull { it.isNotBlank() }?.trim()
            ?.replace(Regex("^\\d+[.)]\\s*"), "")?.trim('"', '“', '”') ?: return null
        if (!line.matches(Regex("[A-Za-z0-9 ,.!?;:'’\\-]+"))) return null
        val tokens = normalizeAnswer(line).split(' ')
        if (tokens.size !in 3..16 || line.length > 200) return null
        if (!Regex("(?<![a-z0-9])${Regex.escape(normalizeAnswer(target))}(?![a-z0-9])")
                .containsMatchIn(normalizeAnswer(line))) return null
        return line
    }
}
