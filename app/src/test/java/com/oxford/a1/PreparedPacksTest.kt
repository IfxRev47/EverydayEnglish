package com.oxford.a1

import android.app.Application
import androidx.room.Room
import com.oxford.a1.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PreparedPacksTest {
    private fun prepared(level: String) = SeedParser.parse(File("../data/prepared/words_$level.json").readText())
    @Test fun everyPreparedWordAndPosMatchesTheOfficialScope() {
        val expected = mapOf("b1" to (805 to 905), "b2" to (1429 to 1578), "c1" to (1314 to 1421))
        expected.forEach { (level, count) ->
            val pack = prepared(level)
            val scope = JSONObject(File("../data/oxford_${level}_scope.json").readText())
            assertEquals(count.first, pack.words.size)
            assertEquals(count.second, pack.words.sumOf { it.meanings.size })
            assertEquals(scope.keys().asSequence().toSet(), pack.words.map { it.key }.toSet())
            pack.words.forEach { word ->
                val poses = scope.getJSONArray(word.key)
                assertEquals((0 until poses.length()).map { poses.getString(it) }.toSet(), word.meanings.map { it.partOfSpeech }.toSet())
                assertTrue(word.meanings.all { it.phonetic.startsWith("/") && it.phonetic.endsWith("/") })
                assertTrue(word.meanings.all { it.chineseMeaning.any { c -> c in '\u4e00'..'\u9fff' } })
            }
            assertEquals(pack, SeedParser.parse(File("src/main/assets/words_$level.json").readText()))
        }
    }
    @Test fun heteronymsAndModernWordsHaveExplicitCorrections() {
        assertEquals("/ˈkɒntent/", prepared("b1").words.single { it.key == "content" }.meanings.single().phonetic)
        assertEquals("/kənˈtent/", prepared("c1").words.single { it.key == "content" }.meanings.single().phonetic)
        assertEquals("/maɪˈnjuːt/", prepared("c1").words.single { it.key == "minute" }.phonetic)
        assertEquals("加密货币", prepared("c1").words.single { it.key == "cryptocurrency" }.meanings.single().chineseMeaning)
    }
    @Test fun futureImportPreservesAllExistingWordRowsAndLearningMarks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WordDatabase::class.java).build()
        try {
            val importer = SeedImporter(db)
            val original = listOf("a1", "a2").map { SeedParser.parse(File("src/main/assets/words_$it.json").readText()) }
            original.forEach { importer.import(it) }
            val dao = db.wordDao()
            dao.observeWords().first().filterIndexed { index, _ -> index % 3 == 0 }.forEach { dao.toggleLearned(it.word.id) }
            val before = dao.observeWords().first()
            val packs = listOf("b1", "b2", "c1").map(::prepared)
            packs.forEach { importer.import(it) }
            val after = dao.observeWords().first()
            val byKey = after.associateBy { it.word.key }
            before.forEach { old ->
                assertEquals(old.word, byKey.getValue(old.word.key).word)
                assertTrue(byKey.getValue(old.word.key).meanings.containsAll(old.meanings))
            }
            assertEquals((original + packs).flatMap { it.words }.map { it.key }.toSet().size, after.size)
            packs.forEach { importer.import(it) }
            assertEquals(after, dao.observeWords().first())
        } finally { db.close() }
    }
}
