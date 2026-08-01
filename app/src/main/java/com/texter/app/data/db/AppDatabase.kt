package com.texter.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.texter.app.data.db.dao.SavedDocumentDao
import com.texter.app.data.db.entity.SavedDocumentEntity

@Database(
    entities = [SavedDocumentEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedDocumentDao(): SavedDocumentDao

    companion object {
        const val DATABASE_NAME = "texter.db"
    }
}
