package com.texter.app.di

import android.content.Context
import androidx.room.Room
import com.texter.app.data.db.AppDatabase
import com.texter.app.data.repository.SavedDocumentsRepository
import com.texter.app.data.saf.DocumentIoRepository
import com.texter.app.data.share.ShareFileRepository

interface AppContainer {
    val database: AppDatabase
    val documentIoRepository: DocumentIoRepository
    val savedDocumentsRepository: SavedDocumentsRepository
    val shareFileRepository: ShareFileRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    override val documentIoRepository: DocumentIoRepository by lazy {
        DocumentIoRepository(context.contentResolver)
    }

    override val savedDocumentsRepository: SavedDocumentsRepository by lazy {
        SavedDocumentsRepository(database.savedDocumentDao(), context.filesDir)
    }

    override val shareFileRepository: ShareFileRepository by lazy {
        ShareFileRepository(context)
    }
}
