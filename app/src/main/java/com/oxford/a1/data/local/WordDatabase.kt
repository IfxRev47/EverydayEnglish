package com.oxford.a1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WordEntity::class, MeaningEntity::class, ImportedPack::class], version = 2, exportSchema = true)
abstract class WordDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meanings ADD COLUMN phonetic TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE meanings ADD COLUMN headword TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE meanings SET phonetic = (SELECT phonetic FROM words WHERE words.id = meanings.wordId), headword = (SELECT word FROM words WHERE words.id = meanings.wordId)")
            }
        }

        fun create(context: Context): WordDatabase = Room.databaseBuilder(
            context.applicationContext, WordDatabase::class.java, "vocabulary.db"
        ).addMigrations(MIGRATION_1_2).build()
    }
}
