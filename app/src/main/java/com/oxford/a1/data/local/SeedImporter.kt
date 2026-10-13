package com.oxford.a1.data.local

import androidx.room.withTransaction

class SeedImporter(private val database: WordDatabase) {
    suspend fun import(pack: SeedPack, progress: (Int) -> Unit = {}) {
        database.withTransaction {
            val dao = database.wordDao()
            val previousVersion = dao.importedVersion(pack.id)
            if ((previousVersion ?: 0) >= pack.version) { progress(pack.words.size); return@withTransaction }
            val existing = dao.allWordRows().associateBy { it.key }.toMutableMap()
            dao.deletePackMeanings(pack.id)
            var completed = 0
            pack.words.chunked(250).forEach { chunk ->
                val additions = chunk.filter { it.key !in existing }.map {
                    WordEntity(key = it.key, word = it.word, phonetic = it.phonetic)
                }
                dao.insertWords(additions).zip(additions).forEach { (id, row) -> existing[row.key] = row.copy(id = id) }
                if (previousVersion != null) dao.updateWords(chunk.map { word ->
                    existing.getValue(word.key).copy(word = word.word, phonetic = word.phonetic)
                })
                dao.insertMeanings(chunk.flatMap { word -> word.meanings.map {
                    MeaningEntity(existing.getValue(word.key).id, it.partOfSpeech, pack.level, it.chineseMeaning,
                        pack.id, it.phonetic.ifBlank { word.phonetic }, word.word)
                } })
                completed += chunk.size
                progress(completed)
            }
            dao.savePack(ImportedPack(pack.id, pack.version))
        }
    }
}
