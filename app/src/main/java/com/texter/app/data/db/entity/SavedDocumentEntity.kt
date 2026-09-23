package com.texter.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_documents")
data class SavedDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    /** Filename under the app's private files/documents/ dir holding the actual content — the
     *  saved copy is the source of truth, not [sourceUri]. */
    val internalFileName: String,
    /** content:// URI this document was opened from, if any. Null for documents created fresh
     *  in-app (not applicable yet, but keeps the door open). */
    val sourceUri: String?,
    /** Write-access snapshot when saved. The editor rechecks the URI grant when reopening,
     *  since temporary Open with grants can expire. */
    val sourceWritable: Boolean,
    val createdAtMillis: Long,
    val lastEditedAtMillis: Long
)
