package com.oxford.a1.grammar

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

data class GrammarExample(val english: String, val chinese: String, val parts: String)
data class GrammarExercise(val prompt: String, val tokens: List<String>, val answers: List<String>, val explanation: String)
data class GrammarLesson(val id: String, val title: String, val goal: String, val rule: String,
    val examples: List<GrammarExample>, val swaps: List<GrammarExample>, val guided: GrammarExercise,
    val independent: GrammarExercise, val ownTask: String)
data class GrammarProgress(val step: Int = 0, val completedDate: String? = null, val ownSentence: String = "")

object GrammarContent {
    fun parse(json: String): List<GrammarLesson> {
        val root = JSONObject(json)
        require(root.getInt("version") == 1)
        val array = root.getJSONArray("lessons")
        fun examples(item: JSONObject, field: String): List<GrammarExample> {
            val values = item.getJSONArray(field)
            return (0 until values.length()).map { i -> values.getJSONObject(i).let {
                GrammarExample(it.getString("english"), it.getString("chinese"), it.getString("parts"))
            } }.also { require(it.isNotEmpty() && it.all { row -> row.english.isNotBlank() && row.chinese.isNotBlank() }) }
        }
        fun exercise(item: JSONObject, field: String): GrammarExercise {
            val value = item.getJSONObject(field)
            val tokens = value.optJSONArray("tokens")
            val answers = value.getJSONArray("answers")
            return GrammarExercise(value.getString("prompt"),
                if (tokens == null) emptyList() else (0 until tokens.length()).map { tokens.getString(it) },
                (0 until answers.length()).map { answers.getString(it) }, value.getString("explanation"))
                .also { require(it.answers.isNotEmpty() && it.answers.all { answer -> answer.isNotBlank() }) }
        }
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            GrammarLesson(it.getString("id"), it.getString("title"), it.getString("goal"), it.getString("rule"),
                examples(it, "examples"), examples(it, "swaps"), exercise(it, "guided"), exercise(it, "independent"), it.getString("ownTask"))
        } }.also { lessons ->
            require(lessons.isNotEmpty() && lessons.map { it.id }.distinct().size == lessons.size)
            lessons.forEach { lesson ->
                require(lesson.id.matches(Regex("g\\d{2}")))
                require(lesson.guided.tokens.isNotEmpty())
                val inventory = lesson.guided.tokens.map { normalizeGrammar(it) }.sorted()
                require(lesson.guided.answers.any { answer -> normalizeGrammar(answer).split(' ').sorted() == inventory })
            }
        }
    }
}

fun normalizeGrammar(text: String): String = text.lowercase(Locale.ROOT).replace('’', '\'')
    .replace(Regex("[^a-z0-9']+"), " ").trim().replace(Regex("\\s+"), " ")
fun matchesGrammar(text: String, answers: List<String>): Boolean = answers.any { normalizeGrammar(text) == normalizeGrammar(it) }

class GrammarProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("grammar-progress", Context.MODE_PRIVATE)
    fun read(id: String) = GrammarProgress(prefs.getInt("${id}_step", 0).coerceIn(0, 4),
        prefs.getString("${id}_completed", null), prefs.getString("${id}_own", "").orEmpty())
    fun lastLesson(): String? = prefs.getString("last", null)
    fun step(id: String, value: Int) {
        require(value in 0..4)
        prefs.edit().putInt("${id}_step", value).putString("last", id).apply()
    }
    fun complete(id: String, own: String) {
        require(own.length <= 300 && own.any { it in 'a'..'z' || it in 'A'..'Z' })
        prefs.edit().putInt("${id}_step", 4).putString("${id}_completed", LocalDate.now().toString())
            .putString("${id}_own", own.trim()).putString("last", id).apply()
    }
}
