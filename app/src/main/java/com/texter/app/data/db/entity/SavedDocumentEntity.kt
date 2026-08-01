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
    /** Whether [sourceUri] was granted with a persistable write permission — gates whether
     *  "update original" is offered. False for URIs handed over via another app's read-only
     *  "Open with" intent. */
    val sourceWritable: Boolean,
    val createdAtMillis: Long,
    val lastEditedAtMillis: Long
)
