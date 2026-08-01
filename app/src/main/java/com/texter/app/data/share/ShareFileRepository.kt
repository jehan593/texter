package com.texter.app.data.share

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.texter.app.text.fileExtensionOf
import java.io.File

data class ShareTarget(val uri: Uri, val mimeType: String)

/** Writes the current editor content to a small cache file and wraps it in a FileProvider URI so
 *  "Share" hands other apps an actual file — correct filename, correct extension-derived mime
 *  type — instead of flattening it down to plain-text-only EXTRA_TEXT, which drops the filename
 *  and breaks any receiving app that expects a real file attachment. */
class ShareFileRepository(private val context: Context) {

    private val shareDir: File by lazy {
        File(context.cacheDir, "shared").apply { mkdirs() }
    }

    fun prepareShareTarget(displayName: String, content: String): ShareTarget {
        // Clear anything left from a previous share so the cache dir doesn't quietly accumulate
        // one file per share forever.
        shareDir.listFiles()?.forEach { it.delete() }

        val fileName = sanitizeFileName(displayName)
        val file = File(shareDir, fileName)
        file.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(fileExtensionOf(fileName).lowercase())
            ?: "text/plain"
        return ShareTarget(uri, mimeType)
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return cleaned.ifBlank { "untitled.txt" }
    }
}
