package com.oxford.a1

import android.app.Application
import com.oxford.a1.grammar.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GrammarTest {
    private fun json() = RuntimeEnvironment.getApplication().assets.open("grammar_beginner.json").bufferedReader().use { it.readText() }
    @Before fun clearOnlyGrammarTestProgress() {
        RuntimeEnvironment.getApplication().getSharedPreferences("grammar-progress", 0).edit().clear().commit()
    }
    @Test fun allEightCoursesHaveValidWordBanksExamplesAndIndependentAnswers() {
        val lessons = GrammarContent.parse(json())
        assertEquals((1..8).map { "g%02d".format(it) }, lessons.map { it.id })
        lessons.forEach { lesson ->
            assertTrue(lesson.examples.size >= 3 && lesson.swaps.size >= 3)
            assertTrue(lesson.goal.isNotBlank() && lesson.rule.isNotBlank() && lesson.ownTask.isNotBlank())
            assertTrue(lesson.examples.all { it.parts.isNotBlank() })
            assertTrue(lesson.guided.answers.all { matchesGrammar(it, lesson.guided.answers) })
            assertFalse(matchesGrammar("", lesson.independent.answers))
        }
        val duplicate = JSONObject(json())
        duplicate.getJSONArray("lessons").put(duplicate.getJSONArray("lessons").getJSONObject(0))
        assertTrue(runCatching { GrammarContent.parse(duplicate.toString()) }.isFailure)
    }
    @Test fun matchingAllowsDeclaredContractionsAndFormattingButNotWordOrderErrors() {
        assertTrue(matchesGrammar("  i WANT water! ", listOf("I want water.")))
        assertTrue(matchesGrammar("I’m happy", listOf("I am happy.", "I'm happy.")))
        assertFalse(matchesGrammar("Water want I", listOf("I want water.")))
        assertFalse(matchesGrammar("She like tea", listOf("She likes tea.")))
        assertFalse(matchesGrammar("Does she likes tea?", listOf("Does she like tea?")))
    }
    @Test fun progressPersistsAndRestartDoesNotEraseCompletionOrPersonalSentence() {
        val app = RuntimeEnvironment.getApplication()
        val store = GrammarProgressStore(app)
        store.step("g01", 3)
        assertEquals(3, GrammarProgressStore(app).read("g01").step)
        store.complete("g01", "I want tea.")
        val completed = GrammarProgressStore(app).read("g01")
        assertNotNull(completed.completedDate)
        assertEquals("I want tea.", completed.ownSentence)
        store.step("g01", 0)
        assertEquals(completed.completedDate, store.read("g01").completedDate)
        assertEquals("I want tea.", store.read("g01").ownSentence)
        assertTrue(runCatching { store.complete("g02", "中文") }.isFailure)
        assertNull(store.read("g02").completedDate)
    }
    @Test fun learnerMustBuildSentenceAndIndependentlyRespondBeforeCompletion() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val vm = GrammarViewModel(app)
        vm.open()
        withTimeout(15_000) {
            while (vm.state.value.loading) { shadowOf(android.os.Looper.getMainLooper()).idle(); delay(10) }
        }
        vm.select("g01")
        vm.advance(); vm.advance(); vm.advance()
        assertEquals(3, vm.state.value.step)
        vm.advance()
        assertEquals(3, vm.state.value.step)
        listOf("I", "want", "tea").forEach { token -> vm.token(vm.state.value.bank.indexOf(token)) }
        vm.checkGuided()
        assertTrue(vm.state.value.guidedCorrect == true)
        vm.advance()
        vm.own("I want coffee."); vm.spoken(true); vm.complete()
        assertFalse(vm.state.value.saved)
        vm.independent("I want water."); vm.checkIndependent(); vm.complete()
        assertTrue(vm.state.value.saved)
        assertEquals("I want coffee.", GrammarProgressStore(app).read("g01").ownSentence)
    }
}
