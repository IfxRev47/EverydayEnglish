package com.oxford.a1

import android.app.Application
import androidx.room.Room
import com.oxford.a1.data.local.*
import com.oxford.a1.data.repository.WordRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class LoadingMigrationTest {
    @Test fun batchImportReportsActualCountsAndDoesNotQueryEachWord() = runBlocking {
        val queries = java.util.concurrent.CopyOnWriteArrayList<String>()
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), WordDatabase::class.java)
            .setQueryCallback({ sql, _ -> queries.add(sql) }, java.util.concurrent.Executor { it.run() }).build()
        try {
            val pack = SeedPack("batch", 1, "A1", (1..600).map {
                SeedWord("word$it", "word$it", "wɜːd", listOf(SeedMeaning("noun", "测试")))
            })
            val progress = mutableListOf<Int>()
            SeedImporter(db).import(pack) { progress.add(it) }
            assertEquals(listOf(250, 500, 600), progress)
            assertEquals(600, db.wordDao().allWordRows().size)
            assertFalse(queries.any { it.contains("WHERE `key` =") })
        } finally { db.close() }
    }

    @Test fun warmStartupSkipsPackParsingAndImportPhases() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(app, WordDatabase::class.java).build()
        try {
            WordRepository(db, app.assets).initialize()
            val before = db.wordDao().allWordRows()
            val repository = WordRepository(db, app.assets)
            val messages = mutableListOf<String>()
            val collector = launch(Dispatchers.Unconfined) { repository.progress.collect { messages.add(it.message) } }
            repository.initialize()
            collector.cancelAndJoin()
            assertFalse(messages.any { it.startsWith("校验新增") || it.startsWith("导入 ") })
            assertEquals(before, db.wordDao().allWordRows())
            val index = JSONArray(app.assets.open("pack_index.json").bufferedReader().use { it.readText() })
            assertEquals(5, index.length())
        } finally { db.close() }
    }

    @Test fun progressBackupRoundTripMergesByKeyWithoutClearingExistingMarks() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val old = Room.inMemoryDatabaseBuilder(app, WordDatabase::class.java).build()
        val new = Room.inMemoryDatabaseBuilder(app, WordDatabase::class.java).build()
        try {
            val oldRepo = WordRepository(old, app.assets)
            val newRepo = WordRepository(new, app.assets)
            val pack = SeedPack("migration", 1, "A1", listOf("apple", "book").map {
                SeedWord(it, it, "ipa", listOf(SeedMeaning("noun", "测试")))
            })
            SeedImporter(old).import(pack)
            SeedImporter(new).import(pack)
            old.wordDao().toggleLearned(old.wordDao().findWord("apple")!!.id)
            new.wordDao().toggleLearned(new.wordDao().findWord("book")!!.id)
            val backup = oldRepo.exportLearned()
            assertEquals(1, newRepo.importLearned(backup))
            assertTrue(new.wordDao().allWordRows().all { it.learned })
            newRepo.importLearned(backup)
            assertTrue(new.wordDao().allWordRows().all { it.learned })
            assertTrue(old.wordDao().findWord("apple")!!.learned)
            val bad = JSONObject(backup).put("schemaVersion", 99).toString()
            assertTrue(runCatching { newRepo.importLearned(bad) }.isFailure)
            assertTrue(new.wordDao().allWordRows().all { it.learned })
        } finally { old.close(); new.close() }
    }
}
