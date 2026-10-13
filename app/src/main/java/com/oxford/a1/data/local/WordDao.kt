package com.oxford.a1.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words")
    suspend fun allWordRows(): List<WordEntity>

    @Query("SELECT * FROM imported_packs")
    suspend fun importedPacks(): List<ImportedPack>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWords(words: List<WordEntity>): List<Long>

    @Update
    suspend fun updateWords(words: List<WordEntity>)

    @Query("UPDATE words SET learned = 1 WHERE `key` IN (:keys)")
    suspend fun markLearned(keys: List<String>): Int
    @Transaction
    @Query("SELECT * FROM words WHERE EXISTS (SELECT 1 FROM meanings WHERE wordId = words.id) ORDER BY word COLLATE NOCASE")
    fun observeWords(): Flow<List<WordWithMeanings>>

    @Query("UPDATE words SET learned = NOT learned WHERE id = :id")
    suspend fun toggleLearned(id: Long)
    @Query("UPDATE words SET learned = 1 WHERE id = :id")
    suspend fun confirmLearned(id: Long)

    @Query("SELECT * FROM words WHERE `key` = :key")
    suspend fun findWord(key: String): WordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWord(word: WordEntity): Long

    // Never replace the row: its ID and the user's learned state must survive content updates.
    @Query("UPDATE words SET word = :word, phonetic = :phonetic WHERE id = :id")
    suspend fun updateContent(id: Long, word: String, phonetic: String)

    @Query("DELETE FROM meanings WHERE packId = :packId")
    suspend fun deletePackMeanings(packId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeanings(meanings: List<MeaningEntity>)

    @Query("SELECT version FROM imported_packs WHERE packId = :packId")
    suspend fun importedVersion(packId: String): Int?

    @Upsert
    suspend fun savePack(pack: ImportedPack)
}
