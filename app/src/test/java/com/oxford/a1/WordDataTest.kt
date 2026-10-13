package com.oxford.a1

import com.oxford.a1.data.local.SeedParser
import com.oxford.a1.data.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

class WordDataTest {
    private fun asset() = File("src/main/assets/words_a1.json").readText()

    @Test fun bundledDataExactlyMatchesOfficialScope() {
        val pack = SeedParser.parse(asset())
        val scope = JSONObject(File("../data/oxford_a1_scope.json").readText())
        assertEquals(898, pack.words.size)
        assertEquals(1076, pack.words.sumOf { it.meanings.size })
        assertEquals("A1", pack.level)
        assertEquals(scope.keys().asSequence().toSet(), pack.words.map { it.key }.toSet())
        pack.words.forEach { word ->
            val official = scope.getJSONArray(word.key)
            assertEquals((0 until official.length()).map { official.getString(it) }.toSet(),
                word.meanings.map { it.partOfSpeech }.toSet())
            assertTrue("Missing Chinese: ${word.word}", word.meanings.all { it.chineseMeaning.any { c -> c in '\u4e00'..'\u9fff' } })
        }
        assertFalse(pack.words.any { it.key == "abandon" })
        assertEquals(listOf("noun"), pack.words.single { it.word == "May" }.meanings.map { it.partOfSpeech })
        assertEquals("I", pack.words.single { it.key == "i" }.word)
    }

    @Test fun a2MatchesOfficialScopeAndKeepsDifferentPronunciations() {
        val pack = SeedParser.parse(File("src/main/assets/words_a2.json").readText())
        val scope = JSONObject(File("../data/oxford_a2_scope.json").readText())
        assertEquals("A2", pack.level)
        assertEquals(867, pack.words.size)
        assertEquals(990, pack.words.sumOf { it.meanings.size })
        assertEquals(scope.keys().asSequence().toSet(), pack.words.map { it.key }.toSet())
        pack.words.forEach { word ->
            val official = scope.getJSONArray(word.key)
            assertEquals((0 until official.length()).map { official.getString(it) }.toSet(), word.meanings.map { it.partOfSpeech }.toSet())
            assertTrue(word.meanings.all { it.phonetic.isNotBlank() && it.chineseMeaning.any { c -> c in '\u4e00'..'\u9fff' } })
        }
        assertEquals("may", pack.words.single { it.key == "may" }.word)
        assertEquals(setOf("ˈrekɔːd", "rɪˈkɔːd"), pack.words.single { it.key == "record" }.meanings.map { it.phonetic }.toSet())
        val a1Keys = SeedParser.parse(asset()).words.map { it.key }.toSet()
        assertEquals(75, pack.words.count { it.key in a1Keys })
        assertEquals(1690, (a1Keys + pack.words.map { it.key }).size)
    }

    @Test fun levelAndPosMustMatchTheSameMeaningAndPreserveWordIdentity() {
        val word = Word(17, "May", "meɪ", true, listOf(
            Meaning("noun", "五月", "A1", "meɪ", "May"),
            Meaning("modal verb", "可能", "A2", "meɪ", "may")
        ))
        assertTrue(filterWords(listOf(word), WordFilter(cefrLevel = "A2", partOfSpeech = "noun")).isEmpty())
        assertTrue(filterWords(listOf(word), WordFilter(cefrLevel = "A2", query = "五月")).isEmpty())
        val a2 = filterWords(listOf(word), WordFilter(cefrLevel = "A2", learned = LearnedFilter.LEARNED)).single()
        assertEquals(17L, a2.id)
        assertTrue(a2.learned)
        assertEquals("may", a2.word)
        assertEquals(listOf("modal verb"), a2.meanings.map { it.partOfSpeech })
    }

    @Test fun parserRejectsDuplicatesEmptyFieldsAndUnsupportedSchema() {
        fun bad(mutator: (JSONObject) -> Unit) {
            val json = JSONObject(asset()).also(mutator)
            assertThrows(IllegalArgumentException::class.java) { SeedParser.parse(json.toString()) }
        }
        bad { it.put("schemaVersion", 2) }
        bad { it.put("cefrLevel", "A0") }
        bad { it.getJSONArray("words").getJSONObject(0).put("phonetic", " ") }
        bad { it.getJSONArray("words").put(it.getJSONArray("words").getJSONObject(0)) }
        bad {
            val meanings = it.getJSONArray("words").getJSONObject(0).getJSONArray("meanings")
            meanings.put(meanings.getJSONObject(0))
        }
    }

    private val words = listOf(
        Word(1, "Apple", "æpəl", false, listOf(Meaning("noun", "苹果", "A1"))),
        Word(2, "be", "biː", true, listOf(Meaning("verb", "是；在", "A1"), Meaning("auxiliary verb", "构成进行时", "A1"))),
        Word(3, "book", "bʊk", false, listOf(Meaning("noun", "书", "A1")))
    )

    @Test fun searchHandlesEnglishCaseChineseWhitespaceAndLiteralSymbols() {
        assertEquals(listOf(1L), filterWords(words, WordFilter(query = " APP ")).map { it.id })
        assertEquals(listOf(1L), filterWords(words, WordFilter(query = "苹果")).map { it.id })
        assertEquals(listOf(2L), filterWords(words, WordFilter(query = "进行时")).map { it.id })
        assertTrue(filterWords(words, WordFilter(query = "%")).isEmpty())
    }

    @Test fun filtersComposeAndSortIsLocaleIndependent() {
        assertEquals(listOf(2L), filterWords(words, WordFilter(partOfSpeech = "auxiliary verb", learned = LearnedFilter.LEARNED)).map { it.id })
        assertTrue(filterWords(words, WordFilter(partOfSpeech = "auxiliary verb", learned = LearnedFilter.UNLEARNED)).isEmpty())
        assertEquals(listOf(3L, 1L), filterWords(words, WordFilter(learned = LearnedFilter.UNLEARNED, ascending = false)).map { it.id })
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf(1L, 2L, 3L), filterWords(words, WordFilter()).map { it.id })
        } finally { Locale.setDefault(locale) }
    }
}
