package com.oxford.a1.data.repository

import android.content.res.AssetManager
import androidx.room.withTransaction
import com.oxford.a1.data.local.*
import com.oxford.a1.data.model.Meaning
import com.oxford.a1.data.model.Word
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LoadProgress(val message: String = "检查词库版本…", val completed: Int = 0, val total: Int = 0) {
    val fraction: Float get() = if (total > 0) (completed.toFloat() / total).coerceIn(0f, 1f) else 0f
}

class WordRepository(private val database: WordDatabase, private val assets: AssetManager) {
    private val ready = MutableStateFlow(false)
    private val initialization = Mutex()
    private val mutableProgress = MutableStateFlow(LoadProgress())
    val progress = mutableProgress.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val words = ready.flatMapLatest { if (it) database.wordDao().observeWords() else flowOf(emptyList()) }.map { entries ->
        entries.map { entry ->
            Word(entry.word.id, entry.word.word, entry.word.phonetic, entry.word.learned,
                entry.meanings.sortedWith(compareBy({ it.cefrLevel }, { it.partOfSpeech })).map {
                    Meaning(it.partOfSpeech, it.chineseMeaning, it.cefrLevel,
                        it.phonetic.ifBlank { entry.word.phonetic }, it.headword.ifBlank { entry.word.word })
                })
        }
    }.flowOn(Dispatchers.Default)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        initialization.withLock {
            if (ready.value) return@withLock
            mutableProgress.value = LoadProgress()
            val index = JSONArray(assets.open("pack_index.json").bufferedReader().use { it.readText() })
            val entries = (0 until index.length()).map { index.getJSONObject(it) }
            check(entries.isNotEmpty()) { "没有找到词库索引" }
            require(entries.map { it.getString("id") }.distinct().size == entries.size)
            val versions = database.wordDao().importedPacks().associate { it.packId to it.version }
            val pending = entries.filter { (versions[it.getString("id")] ?: 0) < it.getInt("version") }
            // Only updated packs are read and parsed. Validate all pending packs before any writes.
            val packs = pending.mapIndexed { i, entry ->
                mutableProgress.value = LoadProgress("校验新增词库 ${i + 1}/${pending.size}", i, pending.size)
                val file = entry.getString("file")
                require(file.matches(Regex("words_[a-z0-9]+\\.json")))
                assets.open(file).bufferedReader().use { SeedParser.parse(it.readText()) }.also {
                    require(it.id == entry.getString("id") && it.version == entry.getInt("version") && it.words.size == entry.getInt("count"))
                }
            }
            val total = packs.sumOf { it.words.size }
            var base = 0
            val importer = SeedImporter(database)
            packs.forEach { pack ->
                importer.import(pack) { count ->
                    mutableProgress.value = LoadProgress("导入 ${pack.level} · 已处理 ${base + count}/$total 条", base + count, total)
                }
                base += pack.words.size
            }
            mutableProgress.value = LoadProgress("读取本地词库…")
            ready.value = true
        }
    }

    suspend fun exportLearned(): String = withContext(Dispatchers.IO) {
        JSONObject().put("schemaVersion", 1).put("type", "everyday-learning-progress")
            .put("learnedWords", JSONArray(database.wordDao().allWordRows().filter { it.learned }.map { it.key })).toString(2)
    }

    suspend fun importLearned(json: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == 1 && root.getString("type") == "everyday-learning-progress") { "不是支持的学习记录文件" }
        val array = root.getJSONArray("learnedWords")
        require(array.length() <= 50_000)
        val keys = (0 until array.length()).map {
            array.getString(it).trim().lowercase(Locale.ROOT).also { key -> require(key.isNotEmpty() && key.length <= 200) }
        }.distinct()
        database.withTransaction { keys.chunked(500).sumOf { database.wordDao().markLearned(it) } }
    }

    suspend fun toggleLearned(id: Long) = database.wordDao().toggleLearned(id)
    suspend fun confirmLearned(id: Long) = database.wordDao().confirmLearned(id)
}
