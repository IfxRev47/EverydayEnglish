package com.oxford.a1

import android.app.Application
import com.oxford.a1.data.model.Meaning
import com.oxford.a1.data.model.Word
import com.oxford.a1.practice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PracticeTest {
    private fun word(id: Long, learned: Boolean = true, level: String = "A1") =
        Word(id, "apple", "æpəl", learned, listOf(Meaning("noun", "苹果", level)))

    @Test fun dailyCountsDeduplicatePersistAndResetAtLocalDateChange() {
        val app = RuntimeEnvironment.getApplication()
        var date = LocalDate.of(2026, 10, 12)
        val store = StudyProgressStore(app) { date }
        store.record(StudyKind.REVIEW, "Apple")
        store.record(StudyKind.REVIEW, "apple")
        store.record(StudyKind.DICTATION, "apple")
        val goals = StudyKind.entries.associateWith { 2 }
        store.saveGoals(goals)
        val reopened = StudyProgressStore(app) { date }.read()
        assertEquals(1, reopened.counts.getValue(StudyKind.REVIEW))
        assertEquals(1, reopened.counts.getValue(StudyKind.DICTATION))
        assertEquals(2f / 6, reopened.fraction, 0.001f)
        date = date.plusDays(1)
        assertTrue(store.read().counts.values.all { it == 0 })
        assertEquals(goals, store.read().goals)
    }

    @Test fun goalValidationDoesNotEraseProgressAndDisabledGoalsDoNotDivideByZero() {
        val store = StudyProgressStore(RuntimeEnvironment.getApplication())
        store.record(StudyKind.REVIEW, "book")
        val before = store.read()
        assertTrue(runCatching { store.saveGoals(StudyKind.entries.associateWith { -1 }) }.isFailure)
        assertEquals(before, store.read())
        val off = store.saveGoals(StudyKind.entries.associateWith { 0 })
        assertEquals(0f, off.fraction)
        assertEquals(1, off.counts.getValue(StudyKind.REVIEW))
    }

    @Test fun randomQueueOnlyIncludesLearnedWordsInChosenLevelWithoutDuplicates() {
        val words = (1L..30L).map { word(it, learned = it % 2L == 0L, level = if (it < 20) "A1" else "A2") }
        val batch = selectReviewWords(words + words, "A1")
        assertEquals(9, batch.size)
        assertTrue(batch.all { it.learned && it.meanings.first().cefrLevel == "A1" })
        assertEquals(batch.size, batch.map { it.id }.distinct().size)
        assertEquals(10, selectReviewWords(words, null).size)
        assertTrue(selectReviewWords(words.map { it.copy(learned = false) }, null).isEmpty())
    }

    @Test fun dictationNormalizationKeepsActualWordsAndContractions() {
        assertEquals(normalizeAnswer("I like apples."), normalizeAnswer("  i LIKE   apples! "))
        assertEquals(normalizeAnswer("I'm happy."), normalizeAnswer("I’m happy"))
        assertNotEquals(normalizeAnswer("I like apples"), normalizeAnswer("I like apple"))
        assertNotEquals(normalizeAnswer("I am happy"), normalizeAnswer("I'm happy"))
    }

    @Test fun aiSentenceValidationRejectsMissingTargetsChineseAndIncompleteThinking() {
        assertEquals("I eat an apple.", PracticePrompt.sentence("<think>plan</think>\nI eat an apple.", "apple"))
        assertNull(PracticePrompt.sentence("<think>I eat an apple.", "apple"))
        assertNull(PracticePrompt.sentence("Here is your sentence:\nI eat an apple.", "apple"))
        assertNull(PracticePrompt.sentence("我吃苹果。", "apple"))
        assertNull(PracticePrompt.sentence("I eat a banana.", "apple"))
        assertNull(PracticePrompt.sentence("I eat pineapple.", "apple"))
        assertTrue(PracticePrompt.build("apple").contains("exact word or phrase"))
    }

    @Test fun skippingAndIncompleteAnswersDoNotCountAndPracticeDoesNotChangeLearnedFlags() {
        val vm = PracticeViewModel(RuntimeEnvironment.getApplication())
        val word = word(1)
        vm.start(listOf(word), PracticeMode.REVIEW, null)
        vm.finish(word)
        assertEquals(0, vm.daily.value.counts.getValue(StudyKind.REVIEW))
        vm.reveal(); vm.finish(word); vm.finish(word)
        assertEquals(1, vm.daily.value.counts.getValue(StudyKind.REVIEW))
        vm.select(setOf(word.id))
        vm.start(listOf(word), PracticeMode.WORD_DICTATION, null)
        vm.answer("banana"); vm.submit("apple"); vm.finish(word)
        assertFalse(vm.state.value.correct)
        assertEquals(1, vm.daily.value.counts.getValue(StudyKind.DICTATION))
        vm.start(listOf(word), PracticeMode.SENTENCE, null)
        vm.next()
        assertEquals(0, vm.daily.value.counts.getValue(StudyKind.SENTENCE))
        assertTrue(word.learned)
    }

    @Test fun selectedPlansPersistAndDayChangeKeepsCustomDictationButResetsToday() {
        val app = RuntimeEnvironment.getApplication()
        var date = LocalDate.of(2026, 10, 12)
        val store = StudyProgressStore(app) { date }
        store.select(setOf(1, 2), false)
        store.select(setOf(3), true)
        store.memorize(1)
        store.memorize(99)
        assertEquals(1, store.read().memorizedCount)
        assertEquals(0.5f, store.read().planFraction)
        assertEquals(setOf(1L, 2L), StudyProgressStore(app) { date }.read().planned)
        store.select(setOf(2), false)
        assertEquals(0, store.read().memorizedCount)
        store.select(setOf(1, 2), false)
        assertEquals(1, store.read().memorizedCount)
        date = date.plusDays(1)
        val next = store.read()
        assertTrue(next.planned.isEmpty() && next.memorized.isEmpty())
        assertEquals(setOf(3L), next.dictation)
    }

    @Test fun customUnlearnedWordsAreIncludedAndQueuesStayIndependent() {
        val vm = PracticeViewModel(RuntimeEnvironment.getApplication())
        val words = listOf(word(1, false), word(2, false), word(3, true))
        vm.select(setOf(1, 2))
        vm.select(setOf(3), true)
        vm.start(words, PracticeMode.TODAY, null)
        assertEquals(setOf(1L, 2L), vm.state.value.ids.toSet())
        val first = words.first { it.id == vm.state.value.currentId }
        vm.reveal(); vm.finish(first)
        assertEquals(1, vm.daily.value.memorizedCount)
        assertFalse(first.learned)
        vm.start(words, PracticeMode.WORD_DICTATION, null, true)
        assertEquals(listOf(3L), vm.state.value.ids)
        vm.start(words, PracticeMode.WORD_DICTATION, null)
        assertEquals(setOf(1L, 2L), vm.state.value.ids.toSet())
        vm.start(words, PracticeMode.REVIEW, null)
        assertEquals(listOf(3L), vm.state.value.ids)
    }

    @Test fun aiRecommendationsOnlyAcceptWordsFromTheActualLevelCandidatePool() {
        val words = listOf(word(1, false).copy(word = "apple"), word(2, false).copy(word = "book"),
            word(3, true).copy(word = "banana"), word(4, false, "B2").copy(word = "abstract"))
        val pool = Recommendation.candidates(words, "A1")
        assertEquals(setOf(1L, 2L), pool.map { it.id }.toSet())
        assertEquals(listOf(1L, 2L), Recommendation.parse("<think>plan</think>\n1. apple\nbook\napple\nbanana\nabstract\ninvented", pool, 10))
        assertEquals(listOf(1L), Recommendation.parse("apple, book", pool, 1))
        assertTrue(Recommendation.parse("<think>apple", pool, 10).isEmpty())
    }

    @Test fun memorizationHistorySurvivesDayChangesPlanEditsAndReopening() {
        val app = RuntimeEnvironment.getApplication()
        var date = LocalDate.of(2026, 10, 12)
        val store = StudyProgressStore(app) { date }
        store.select(setOf(1, 2), false)
        store.memorize(1); store.memorize(1); store.memorize(99)
        store.select(emptySet(), false)
        assertEquals(listOf(MemorizationDay("2026-10-12", setOf(1))), store.history())
        date = date.plusDays(1)
        store.select(setOf(1, 2), false)
        store.memorize(1); store.memorize(2)
        val expected = listOf(MemorizationDay("2026-10-13", setOf(1, 2)), MemorizationDay("2026-10-12", setOf(1)))
        assertEquals(expected, StudyProgressStore(app) { date }.history())
        date = date.plusDays(5)
        assertEquals(expected, store.history())
        assertTrue(store.read().memorized.isEmpty())
    }

    @Test fun upgradeArchivesRetainedOldDayBeforeResetAndMergesExistingHistory() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("study-progress", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("date", "2026-10-12")
            .putStringSet("memorized", setOf("1", "2"))
            .putStringSet("planned", setOf("2"))
            .putStringSet("history_2026-10-12", setOf("3"))
            .putStringSet("dictation", setOf("4")).commit()
        val store = StudyProgressStore(app) { LocalDate.of(2026, 10, 13) }
        val current = store.read()
        assertTrue(current.planned.isEmpty() && current.memorized.isEmpty())
        assertEquals(setOf(4L), current.dictation)
        assertEquals(listOf(MemorizationDay("2026-10-12", setOf(1, 2, 3))), store.history())
        assertEquals(store.history(), store.history())
    }
}
