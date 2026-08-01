package com.texter.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.texter.app.data.db.entity.SavedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDocumentDao {
    @Query("SELECT * FROM saved_documents ORDER BY lastEditedAtMillis DESC")
    fun observeAll(): Flow<List<SavedDocumentEntity>>

    @Query("SELECT * FROM saved_documents WHERE id = :id")
    suspend fun findById(id: Long): SavedDocumentEntity?

    @Insert
    suspend fun insert(document: SavedDocumentEntity): Long

    @Update
    suspend fun update(document: SavedDocumentEntity)

    @Delete
    suspend fun delete(document: SavedDocumentEntity)
}
