package com.oxford.a1.practice

import android.content.Context
import java.time.LocalDate

enum class StudyKind(val label: String, val defaultGoal: Int) {
    REVIEW("随机回顾", 20), DICTATION("听写", 5), SENTENCE("造句练习", 3)
}

data class MemorizationDay(val date: String, val wordIds: Set<Long>)

data class DailyProgress(val date: String, val goals: Map<StudyKind, Int>, val counts: Map<StudyKind, Int>,
    val planned: Set<Long> = emptySet(), val memorized: Set<Long> = emptySet(), val dictation: Set<Long> = emptySet()) {
    val memorizedCount: Int get() = planned.intersect(memorized).size
    val planFraction: Float get() = if (planned.isEmpty()) 0f else memorizedCount.toFloat() / planned.size
    val fraction: Float get() {
        val total = goals.values.sum()
        return if (total == 0) 0f else StudyKind.entries.sumOf { minOf(counts.getValue(it), goals.getValue(it)) }.toFloat() / total
    }
}

/** Separate from vocabulary storage: practice never rewrites learned flags. */
class StudyProgressStore(context: Context, private val today: () -> LocalDate = { LocalDate.now() }) {
    private val prefs = context.getSharedPreferences("study-progress", Context.MODE_PRIVATE)
    fun read(): DailyProgress {
        val date = today().toString()
        // Archive 1.8's retained completion set before its daily reset, including on upgrade.
        val previousDate = prefs.getString("date", null)
        val completed = ids("memorized")
        val archiveKey = previousDate?.let { "history_$it" }
        val missingHistory = if (archiveKey != null) completed - ids(archiveKey) else emptySet()
        val editor = prefs.edit()
        var changed = false
        if (archiveKey != null && missingHistory.isNotEmpty()) {
            editor.putStringSet(archiveKey, (ids(archiveKey) + completed).map { it.toString() }.toSet())
            changed = true
        }
        if (prefs.getString("date", null) != date) {
            editor.putString("date", date)
            StudyKind.entries.forEach { editor.remove("done_${it.name}") }
            editor.remove("planned").remove("memorized")
            changed = true
        }
        if (changed) editor.apply()
        return DailyProgress(date,
            StudyKind.entries.associateWith { prefs.getInt("goal_${it.name}", it.defaultGoal).coerceIn(0, 100) },
            StudyKind.entries.associateWith { prefs.getStringSet("done_${it.name}", emptySet()).orEmpty().size },
            ids("planned"), ids("memorized"), ids("dictation"))
    }
    private fun ids(field: String): Set<Long> = prefs.getStringSet(field, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    fun select(ids: Set<Long>, dictation: Boolean): DailyProgress {
        require(ids.size <= 200 && ids.all { it > 0 })
        read()
        prefs.edit().putStringSet(if (dictation) "dictation" else "planned", ids.map { it.toString() }.toSet()).apply()
        return read()
    }
    fun memorize(id: Long): DailyProgress {
        val current = read()
        if (id in current.planned) prefs.edit()
            .putStringSet("memorized", (current.memorized + id).map { it.toString() }.toSet())
            .putStringSet("history_${current.date}", (ids("history_${current.date}") + id).map { it.toString() }.toSet())
            .apply()
        return read()
    }
    fun history(): List<MemorizationDay> {
        read()
        return prefs.all.keys.filter { it.matches(Regex("history_\\d{4}-\\d{2}-\\d{2}")) }
            .map { key -> MemorizationDay(key.removePrefix("history_"), ids(key)) }
            .filter { it.wordIds.isNotEmpty() }.sortedByDescending { it.date }
    }
    fun saveGoals(goals: Map<StudyKind, Int>): DailyProgress {
        require(StudyKind.entries.all { goals[it] in 0..100 })
        val editor = prefs.edit()
        goals.forEach { (kind, goal) -> editor.putInt("goal_${kind.name}", goal) }
        editor.apply()
        return read()
    }
    fun record(kind: StudyKind, key: String): DailyProgress {
        require(key.isNotBlank())
        read()
        val field = "done_${kind.name}"
        val keys = prefs.getStringSet(field, emptySet()).orEmpty().toMutableSet()
        keys.add(key.lowercase(java.util.Locale.ROOT))
        prefs.edit().putStringSet(field, keys).apply()
        return read()
    }
}
