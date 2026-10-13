package com.oxford.a1.data.local

import androidx.room.*

@Entity(tableName = "words", indices = [Index(value = ["key"], unique = true)])
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val word: String,
    val phonetic: String,
    val learned: Boolean = false
)

@Entity(
    tableName = "meanings",
    primaryKeys = ["wordId", "partOfSpeech", "cefrLevel"],
    foreignKeys = [ForeignKey(
        entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("wordId"), Index("packId")]
)
data class MeaningEntity(
    val wordId: Long,
    val partOfSpeech: String,
    val cefrLevel: String,
    val chineseMeaning: String,
    val packId: String,
    @ColumnInfo(defaultValue = "''") val phonetic: String = "",
    @ColumnInfo(defaultValue = "''") val headword: String = ""
)

@Entity(tableName = "imported_packs")
data class ImportedPack(@PrimaryKey val packId: String, val version: Int)

data class WordWithMeanings(
    @Embedded val word: WordEntity,
    @Relation(parentColumn = "id", entityColumn = "wordId") val meanings: List<MeaningEntity>
)
