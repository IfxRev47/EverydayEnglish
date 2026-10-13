package com.oxford.a1

import android.app.Application
import androidx.room.Room
import com.oxford.a1.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RoomImportTest {
    private lateinit var database: WordDatabase
    private lateinit var importer: SeedImporter
    private val pack = SeedPack("test-a1", 1, "A1", listOf(
        SeedWord("be", "be", "biː", listOf(SeedMeaning("verb", "是"), SeedMeaning("auxiliary verb", "助动词")))
    ))

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WordDatabase::class.java).build()
        importer = SeedImporter(database)
    }
    @After fun close() { database.close() }

    @Test fun importIsIdempotentAndContentUpgradePreservesProgress() = runBlocking {
        importer.import(pack)
        val dao = database.wordDao()
        val id = dao.findWord("be")!!.id
        dao.toggleLearned(id)
        importer.import(pack)
        assertEquals(1, dao.observeWords().first().size)
        assertTrue(dao.findWord("be")!!.learned)
        importer.import(pack.copy(version = 2, words = pack.words.map {
            it.copy(phonetic = "biː; bi", meanings = listOf(SeedMeaning("verb", "是；在")))
        }))
        val updated = dao.observeWords().first().single()
        assertEquals(id, updated.word.id)
        assertTrue(updated.word.learned)
        assertEquals("biː; bi", updated.word.phonetic)
        assertEquals(1, updated.meanings.size)
        assertEquals("是；在", updated.meanings.single().chineseMeaning)
        assertEquals(2, dao.importedVersion(pack.id))
    }

    @Test fun failedUpgradeRollsBackContentAndVersion() = runBlocking {
        importer.import(pack)
        val invalid = pack.copy(version = 2, words = pack.words.map { it.copy(meanings = it.meanings + it.meanings.first()) })
        var failed = false
        try { importer.import(invalid) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(1, database.wordDao().importedVersion(pack.id))
        assertEquals(2, database.wordDao().observeWords().first().single().meanings.size)
    }

    @Test fun anotherLevelReusesWordAndPreservesSeparateMeanings() = runBlocking {
        importer.import(pack)
        val id = database.wordDao().findWord("be")!!.id
        importer.import(pack.copy(id = "test-a2", level = "A2"))
        val words = database.wordDao().observeWords().first()
        assertEquals(1, words.size)
        assertEquals(id, words.single().word.id)
        assertEquals(setOf("A1", "A2"), words.single().meanings.map { it.cefrLevel }.toSet())
    }

    @Test fun fullBundledAssetImportsIntoActualRoomDatabase() = runBlocking {
        val json = RuntimeEnvironment.getApplication().assets.open("words_a1.json").bufferedReader().use { it.readText() }
        importer.import(SeedParser.parse(json))
        val words = database.wordDao().observeWords().first()
        assertEquals(898, words.size)
        assertEquals(1076, words.sumOf { it.meanings.size })
        assertEquals("苹果", words.single { it.word.word == "apple" }.meanings.single().chineseMeaning)
    }
}
