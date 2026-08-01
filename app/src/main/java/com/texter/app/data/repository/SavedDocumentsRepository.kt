package com.texter.app.data.repository

import com.texter.app.data.db.dao.SavedDocumentDao
import com.texter.app.data.db.entity.SavedDocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Owns the app-private copy of every saved document's content (a file per document under
 *  files/documents/) alongside its Room metadata row. The saved copy is always the source of
 *  truth — nothing here depends on the original external URI still being reachable. */
class SavedDocumentsRepository(
    private val dao: SavedDocumentDao,
    filesDir: File
) {
    private val documentsDir: File by lazy {
        File(filesDir, "documents").apply { mkdirs() }
    }

    fun observeAll(): Flow<List<SavedDocumentEntity>> = dao.observeAll()

    suspend fun findById(id: Long): SavedDocumentEntity? = dao.findById(id)

    suspend fun readContent(document: SavedDocumentEntity): String = withContext(Dispatchers.IO) {
        File(documentsDir, document.internalFileName).readText(Charsets.UTF_8)
    }

    suspend fun create(
        displayName: String,
        content: String,
        sourceUri: String?,
        sourceWritable: Boolean
    ): SavedDocumentEntity = withContext(Dispatchers.IO) {
        val internalFileName = "${UUID.randomUUID()}.txt"
        File(documentsDir, internalFileName).writeText(content, Charsets.UTF_8)
        val now = System.currentTimeMillis()
        val entity = SavedDocumentEntity(
            displayName = displayName,
            internalFileName = internalFileName,
            sourceUri = sourceUri,
            sourceWritable = sourceWritable,
            createdAtMillis = now,
            lastEditedAtMillis = now
        )
        entity.copy(id = dao.insert(entity))
    }

    /** Rewrites the saved copy and bumps [SavedDocumentEntity.lastEditedAtMillis] so the
     *  documents list reflects last-edited, not just last-opened — see project memory on the
     *  "recent files" freshness question this was chosen to solve. */
    suspend fun updateContent(document: SavedDocumentEntity, newContent: String): SavedDocumentEntity =
        withContext(Dispatchers.IO) {
            File(documentsDir, document.internalFileName).writeText(newContent, Charsets.UTF_8)
            val updated = document.copy(lastEditedAtMillis = System.currentTimeMillis())
            dao.update(updated)
            updated
        }

    suspend fun rename(document: SavedDocumentEntity, newDisplayName: String): SavedDocumentEntity {
        val updated = document.copy(displayName = newDisplayName)
        dao.update(updated)
        return updated
    }

    suspend fun delete(document: SavedDocumentEntity) = withContext(Dispatchers.IO) {
        File(documentsDir, document.internalFileName).delete()
        dao.delete(document)
    }
}
