package com.texter.app.data.saf

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Thrown when a file opened via [DocumentIoRepository.readText] doesn't look like text — a
 *  safety net for the app's broad "Open with" registration (it shows up as an option for any
 *  file, since most providers report code/config files as generic application/octet-stream
 *  rather than a text MIME type, and there's no reliable way to pre-filter that from the
 *  manifest alone). */
class BinaryContentException(message: String) : IOException(message)

class DocumentIoRepository(private val contentResolver: ContentResolver) {

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Unable to open $uri for reading")
        if (looksBinary(bytes)) throw BinaryContentException("$uri does not look like a text file")
        bytes.toString(Charsets.UTF_8)
    }

    suspend fun writeText(uri: Uri, content: String) = withContext(Dispatchers.IO) {
        // "wt" = write + truncate, so a shorter re-save doesn't leave trailing bytes from the
        // previous, longer content behind.
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Unable to open $uri for writing")
    }

    fun queryDisplayName(uri: Uri): String {
        val rawName = queryRawDisplayName(uri) ?: uri.lastPathSegment ?: "untitled"
        return ensureExtension(rawName, uri)
    }

    private fun queryRawDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    /** Some senders (WhatsApp's shared-document cache, notably) report a display name with no
     *  extension at all — falls back to guessing one from the URI's actual MIME type so syntax
     *  highlighting and any later save/share still land on a sane file extension. This can't
     *  recover the sender's original filename, only patch a missing extension onto whatever name
     *  they did report. */
    private fun ensureExtension(name: String, uri: Uri): String {
        if (name.contains('.')) return name
        val mimeType = contentResolver.getType(uri) ?: return name
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: return name
        return "$name.$extension"
    }

    /** Best-effort persistable read+write grant for [uri]. Returns false (rather than throwing)
     *  when the grant doesn't support it — e.g. a URI handed over via another app's read-only
     *  "Open with" intent — since that's an expected, common case, not an error: the caller just
     *  won't offer "update original" for that document. */
    fun tryPersistWritablePermission(uri: Uri): Boolean = try {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        true
    } catch (_: SecurityException) {
        false
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        // A NUL byte anywhere in a real text file is vanishingly rare (UTF-16/32 without a BOM
        // being the main exception, which this simple check doesn't special-case) but routine in
        // binary formats — checking a leading sample is the standard, cheap heuristic for this.
        val sampleSize = minOf(bytes.size, 8_000)
        for (i in 0 until sampleSize) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }
}
