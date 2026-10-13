package com.oxford.a1.data.local

import org.json.JSONObject
import java.util.Locale

data class SeedMeaning(val partOfSpeech: String, val chineseMeaning: String, val phonetic: String = "")
data class SeedWord(val key: String, val word: String, val phonetic: String, val meanings: List<SeedMeaning>)
data class SeedPack(val id: String, val version: Int, val level: String, val words: List<SeedWord>)

object SeedParser {
    private val levels = setOf("A1", "A2", "B1", "B2", "C1", "C2")
    private val partsOfSpeech = setOf("noun", "verb", "adjective", "adverb", "preposition", "pronoun",
        "determiner", "auxiliary verb", "modal verb", "conjunction", "exclamation", "number",
        "ordinal number", "indefinite article", "definite article", "infinitive marker", "linking verb")

    fun parse(json: String): SeedPack {
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == 1) { "不支持的词库格式" }
        val id = root.nonBlank("datasetId")
        val version = root.getInt("version")
        require(version > 0) { "词库版本必须大于 0" }
        val level = root.nonBlank("cefrLevel")
        require(level in levels) { "无效的 CEFR 等级：$level" }
        val array = root.getJSONArray("words")
        require(array.length() > 0) { "词库不能为空" }
        val keys = mutableSetOf<String>()
        val words = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val word = item.nonBlank("word")
            val key = word.lowercase(Locale.ROOT)
            require(keys.add(key)) { "重复单词：$word" }
            val phonetic = item.nonBlank("phonetic")
            val senses = item.getJSONArray("meanings")
            require(senses.length() > 0) { "$word 没有释义" }
            val seen = mutableSetOf<String>()
            val meanings = (0 until senses.length()).map { j ->
                val sense = senses.getJSONObject(j)
                val pos = sense.nonBlank("partOfSpeech")
                require(pos in partsOfSpeech && seen.add(pos)) { "$word 的词性无效或重复：$pos" }
                SeedMeaning(pos, sense.nonBlank("chineseMeaning"),
                    if (sense.has("phonetic")) sense.nonBlank("phonetic") else phonetic)
            }
            SeedWord(key, word, phonetic, meanings)
        }
        return SeedPack(id, version, level, words)
    }

    private fun JSONObject.nonBlank(key: String): String = getString(key).trim().also {
        require(it.isNotEmpty()) { "$key 不可为空" }
    }
}
