package com.oxford.a1

import android.app.Application
import androidx.room.Room
import com.oxford.a1.data.local.*
import com.oxford.a1.data.repository.WordRepository
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
class UpgradePreservesProgressTest {
    @Test fun fullVersionOneDatabaseRetainsEveryWordAndMarkAfterUpgradeAndRestart() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "upgrade-regression.db" // Robolectric's isolated test directory, never the real device DB.
        context.deleteDatabase(name)
        val a1 = SeedParser.parse(context.assets.open("words_a1.json").bufferedReader().use { it.readText() })
        val originals = a1.words.mapIndexed { index, word ->
            WordEntity(10L + index * 2, word.key, word.word, word.phonetic, learned = index % 3 == 0)
        }.associateBy { it.key }

        // Create the ACTUAL exported v1 schema, not a v2 database masquerading as an old one.
        val schema = JSONObject(File("schemas/com.oxford.a1.data.local.WordDatabase/1.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.beginTransaction()
            try {
                a1.words.forEach { word ->
                    val original = originals.getValue(word.key)
                    old.execSQL("INSERT INTO words (id, `key`, word, phonetic, learned) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(original.id, original.key, original.word, original.phonetic, if (original.learned) 1 else 0))
                    word.meanings.forEach { meaning ->
                        old.execSQL("INSERT INTO meanings (wordId, partOfSpeech, cefrLevel, chineseMeaning, packId) VALUES (?, ?, ?, ?, ?)",
                            arrayOf(original.id, meaning.partOfSpeech, "A1", meaning.chineseMeaning, a1.id))
                    }
                }
                old.execSQL("INSERT INTO imported_packs (packId, version) VALUES (?, ?)", arrayOf(a1.id, a1.version))
                old.version = 1
                old.setTransactionSuccessful()
            } finally { old.endTransaction() }
        }

        fun open() = Room.databaseBuilder(context, WordDatabase::class.java, name)
            .addMigrations(WordDatabase.MIGRATION_1_2).build()

        suspend fun verify(database: WordDatabase) {
            val records = database.wordDao().observeWords().first()
            assertEquals(4953, records.size)
            assertEquals(5970, records.sumOf { it.meanings.size })
            originals.forEach { (key, before) -> assertEquals("Changed existing record: $key", before, database.wordDao().findWord(key)) }
            assertTrue(records.filter { it.word.key !in originals }.all { !it.word.learned })
            val expectedA1 = a1.words.flatMap { word -> word.meanings.map { word.key to it } }
                .associate { (key, sense) -> (key to sense.partOfSpeech) to sense.chineseMeaning }
            val actualA1 = records.flatMap { record -> record.meanings.filter { it.cefrLevel == "A1" }.map {
                (record.word.key to it.partOfSpeech) to it.chineseMeaning
            } }.toMap()
            assertEquals(expectedA1, actualA1)
            val close = records.single { it.word.key == "close" }
            assertEquals("kləʊz", close.meanings.single { it.cefrLevel == "A1" }.phonetic)
            assertEquals("kləʊs", close.meanings.single { it.cefrLevel == "A2" }.phonetic)
            val may = records.single { it.word.key == "may" }
            assertEquals("May", may.meanings.single { it.cefrLevel == "A1" }.headword)
            assertEquals("may", may.meanings.single { it.cefrLevel == "A2" }.headword)
            assertEquals(1, database.wordDao().importedVersion("oxford-a1"))
            assertEquals(1, database.wordDao().importedVersion("oxford-a2"))
        }

        try {
            val firstOpen = open()
            try {
                WordRepository(firstOpen, context.assets).initialize()
                verify(firstOpen)
            } finally { firstOpen.close() }
            val reopened = open()
            try {
                WordRepository(reopened, context.assets).initialize()
                verify(reopened)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
