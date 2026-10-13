package com.oxford.a1

import android.app.Application
import com.oxford.a1.data.local.WordDatabase
import com.oxford.a1.data.repository.WordRepository

class VocabularyApplication : Application() {
    private val database by lazy { WordDatabase.create(this) }
    val repository by lazy { WordRepository(database, assets) }
}
