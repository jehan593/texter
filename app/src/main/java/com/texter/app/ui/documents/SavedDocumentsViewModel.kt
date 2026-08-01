package com.texter.app.ui.documents

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texter.app.data.db.entity.SavedDocumentEntity
import com.texter.app.data.repository.SavedDocumentsRepository
import com.texter.app.data.saf.BinaryContentException
import com.texter.app.data.saf.DocumentIoRepository
import com.texter.app.data.share.ShareFileRepository
import com.texter.app.data.share.ShareTarget
import com.texter.app.ui.editor.EditorSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SavedDocumentsViewModel(
    private val savedDocumentsRepository: SavedDocumentsRepository,
    private val documentIoRepository: DocumentIoRepository,
    private val shareFileRepository: ShareFileRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    var searchText by mutableStateOf("")
        private set

    /** Plain substring match (case-insensitive) over the document name — the saved-documents
     *  list is small enough that this doesn't need DB-level search. */
    val documents: StateFlow<List<SavedDocumentEntity>> =
        combine(savedDocumentsRepository.observeAll(), searchQuery) { all, query ->
            if (query.isBlank()) all else all.filter { it.displayName.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _contentToCopy = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val contentToCopy: SharedFlow<String> = _contentToCopy.asSharedFlow()

    private val _shareRequests = MutableSharedFlow<ShareTarget>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<ShareTarget> = _shareRequests.asSharedFlow()

    fun onSearchChanged(text: String) {
        searchText = text
        searchQuery.value = text
    }

    /** Reads [uri] and hands back an [EditorSource.Opened] for the editor to load — deliberately
     *  does NOT touch the saved list. Only an explicit "Save in app" from the editor does that.
     *  [requestWritePermission] should be true when [uri] came from our own SAF picker (always
     *  writable) and false for another app's "Open with" intent (often read-only) — either way
     *  the resulting writability is whatever [DocumentIoRepository.tryPersistWritablePermission]
     *  could really obtain, not just what was requested. */
    fun prepareOpen(
        uri: Uri,
        requestWritePermission: Boolean,
        onReady: (EditorSource.Opened) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val sourceWritable = requestWritePermission &&
                    documentIoRepository.tryPersistWritablePermission(uri)
                val content = documentIoRepository.readText(uri)
                val displayName = documentIoRepository.queryDisplayName(uri)
                onReady(EditorSource.Opened(displayName, content, uri.toString(), sourceWritable))
            } catch (e: BinaryContentException) {
                _messages.tryEmit("That doesn't look like a text file")
            } catch (e: Exception) {
                _messages.tryEmit("Couldn't open that file")
            }
        }
    }

    fun rename(document: SavedDocumentEntity, newDisplayName: String) {
        val trimmed = newDisplayName.trim()
        if (trimmed.isEmpty() || trimmed == document.displayName) return
        viewModelScope.launch { savedDocumentsRepository.rename(document, trimmed) }
    }

    fun requestCopyContent(document: SavedDocumentEntity) {
        viewModelScope.launch {
            val content = savedDocumentsRepository.readContent(document)
            _contentToCopy.tryEmit(content)
        }
    }

    fun requestShare(document: SavedDocumentEntity) {
        viewModelScope.launch {
            val content = savedDocumentsRepository.readContent(document)
            _shareRequests.tryEmit(shareFileRepository.prepareShareTarget(document.displayName, content))
        }
    }

    fun saveToLocalStorage(document: SavedDocumentEntity, targetUri: Uri) {
        viewModelScope.launch {
            try {
                val content = savedDocumentsRepository.readContent(document)
                documentIoRepository.writeText(targetUri, content)
                _messages.tryEmit("Saved")
            } catch (e: Exception) {
                _messages.tryEmit("Couldn't save to that location")
            }
        }
    }

    fun delete(document: SavedDocumentEntity) {
        viewModelScope.launch { savedDocumentsRepository.delete(document) }
    }
}
