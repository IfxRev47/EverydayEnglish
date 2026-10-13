package com.oxford.a1.audio

/** English-only reading for bilingual learning output; never send an oversized TTS utterance. */
object SpeechText {
    fun chunks(text: String, limit: Int = 1000): List<String> {
        require(limit > 0)
        val clean = text.replace(Regex("(?is)<think>.*?(</think>|$)"), " ")
            .replace(Regex("(?s)```.*?(```|$)"), " ")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("[^A-Za-z0-9\\s.,!?;:'’\\-]"), " ")
            .replace(Regex("(?m)^\\s*\\d+[.)]\\s*"), "")
            .replace(Regex("\\s+"), " ").trim()
        if (!clean.any { it in 'A'..'Z' || it in 'a'..'z' }) return emptyList()
        val result = mutableListOf<String>()
        var remaining = clean
        while (remaining.isNotEmpty()) {
            val end = if (remaining.length <= limit) remaining.length else {
                remaining.lastIndexOf(' ', limit).takeIf { it > 0 } ?: limit
            }
            remaining.take(end).trim().takeIf { it.any { c -> c.isLetter() } }?.let(result::add)
            remaining = remaining.drop(end).trimStart()
        }
        return result
    }
}
