package com.texter.app.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.texter.app.data.db.entity.SavedDocumentEntity
import com.texter.app.data.repository.SavedDocumentsRepository
import com.texter.app.data.saf.DocumentIoRepository
import com.texter.app.data.share.ShareFileRepository
import com.texter.app.data.share.ShareTarget
import com.texter.app.text.SearchMatch
import com.texter.app.text.findMatches
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** [id] is null until the file is saved in the app. */
data class EditorDocumentInfo(
    val id: Long?,
    val displayName: String,
    val sourceUri: String?,
    val sourceWritable: Boolean
)

class EditorViewModel(
    private val source: EditorSource,
    private val savedDocumentsRepository: SavedDocumentsRepository,
    private val documentIoRepository: DocumentIoRepository,
    private val shareFileRepository: ShareFileRepository
) : ViewModel() {

    private var savedEntity: SavedDocumentEntity? = null

    var documentInfo by mutableStateOf<EditorDocumentInfo?>(null)
        private set

    var textFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    // Files not yet saved in the app show the unsaved marker even before editing.
    private var lastSavedText: String? = null
    private var lastKeptText by mutableStateOf("")

    var isSaving by mutableStateOf(false)
        private set

    val hasUnsavedChanges: Boolean
        get() = documentInfo != null && !isLoading && textFieldValue.text != lastKeptText

    var isLoading by mutableStateOf(true)
        private set

    val isDirty: Boolean
        get() = lastSavedText == null || textFieldValue.text != lastSavedText

    var searchActive by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var matches by mutableStateOf<List<SearchMatch>>(emptyList())
        private set

    var currentMatchIndex by mutableIntStateOf(-1)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // The screen owns the text layout needed to scroll to a match.
    private val _scrollToMatchRequests = MutableSharedFlow<SearchMatch>(extraBufferCapacity = 1)
    val scrollToMatchRequests: SharedFlow<SearchMatch> = _scrollToMatchRequests.asSharedFlow()

    init {
        viewModelScope.launch {
            when (source) {
                is EditorSource.Saved -> {
                    val doc = savedDocumentsRepository.findById(source.documentId)
                    if (doc != null) {
                        savedEntity = doc
                        val content = savedDocumentsRepository.readContent(doc)
                        // Write access may have expired since the file was saved.
                        val sourceWritable = doc.sourceUri?.let {
                            documentIoRepository.hasWritePermission(Uri.parse(it))
                        } ?: false
                        documentInfo = EditorDocumentInfo(doc.id, doc.displayName, doc.sourceUri, sourceWritable)
                        textFieldValue = TextFieldValue(content)
                        lastSavedText = content
                    }
                }
                is EditorSource.Opened -> {
                    documentInfo = EditorDocumentInfo(
                        id = null,
                        displayName = source.displayName,
                        sourceUri = source.sourceUri,
                        sourceWritable = source.sourceWritable
                    )
                    textFieldValue = TextFieldValue(source.content)
                }
                is EditorSource.New -> {
                    documentInfo = EditorDocumentInfo(
                        id = null,
                        displayName = source.displayName,
                        sourceUri = null,
                        sourceWritable = false
                    )
                    textFieldValue = TextFieldValue("")
                }
            }
            lastKeptText = textFieldValue.text
            isLoading = false
        }
    }

    fun onTextChanged(newValue: TextFieldValue) {
        val textChanged = newValue.text != textFieldValue.text
        textFieldValue = newValue
        if (textChanged && searchActive && searchQuery.isNotEmpty()) {
            recomputeMatches(moveToFirst = false)
        }
    }

    /** Only this action adds a file to the app's saved list. */
    fun save(onSaved: () -> Unit = {}) {
        val info = documentInfo ?: return
        if (isSaving) return
        val content = textFieldValue.text
        isSaving = true
        viewModelScope.launch {
            try {
                val entity = savedEntity
                val updated = if (entity == null) {
                    savedDocumentsRepository.create(
                        displayName = info.displayName,
                        content = content,
                        sourceUri = info.sourceUri,
                        sourceWritable = info.sourceWritable
                    )
                } else {
                    savedDocumentsRepository.updateContent(entity, content)
                }
                savedEntity = updated
                documentInfo = info.copy(id = updated.id)
                lastSavedText = content
                lastKeptText = content
                _messages.tryEmit("Saved in app")
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Couldn't save the file")
            } finally {
                isSaving = false
            }
        }
    }

    fun rename(newDisplayName: String) {
        val info = documentInfo ?: return
        val trimmed = newDisplayName.trim()
        if (trimmed.isEmpty() || trimmed == info.displayName) return
        documentInfo = info.copy(displayName = trimmed)
        val entity = savedEntity ?: return
        viewModelScope.launch {
            savedEntity = savedDocumentsRepository.rename(entity, trimmed)
        }
    }

    fun updateOriginal() {
        val info = documentInfo ?: return
        val sourceUri = info.sourceUri ?: return
        if (!info.sourceWritable) return
        if (isSaving) return
        if (!documentIoRepository.hasWritePermission(Uri.parse(sourceUri))) {
            documentInfo = info.copy(sourceWritable = false)
            _messages.tryEmit("Write access expired. Reopen the file to update it.")
            return
        }
        val content = textFieldValue.text
        isSaving = true
        viewModelScope.launch {
            try {
                documentIoRepository.writeText(Uri.parse(sourceUri), content)
                lastKeptText = content
                _messages.tryEmit("Original file updated")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Couldn't update the original file")
            } finally {
                isSaving = false
            }
        }
    }

    fun saveToLocalStorage(targetUri: Uri) {
        if (isSaving) return
        val content = textFieldValue.text
        isSaving = true
        viewModelScope.launch {
            try {
                documentIoRepository.writeText(targetUri, content)
                lastKeptText = content
                _messages.tryEmit("Saved")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Couldn't save to that location")
            } finally {
                isSaving = false
            }
        }
    }

    fun prepareShareTarget(): ShareTarget? {
        val info = documentInfo ?: return null
        return shareFileRepository.prepareShareTarget(info.displayName, textFieldValue.text)
    }

    fun clearText() {
        onTextChanged(TextFieldValue(""))
    }

    fun toggleSearch() {
        searchActive = !searchActive
        if (!searchActive) {
            searchQuery = ""
            matches = emptyList()
            currentMatchIndex = -1
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        recomputeMatches(moveToFirst = true)
    }

    fun nextMatch() {
        if (matches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % matches.size
        moveSelectionTo(matches[currentMatchIndex])
    }

    fun previousMatch() {
        if (matches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex - 1 + matches.size) % matches.size
        moveSelectionTo(matches[currentMatchIndex])
    }

    private fun recomputeMatches(moveToFirst: Boolean) {
        matches = findMatches(textFieldValue.text, searchQuery)
        if (matches.isEmpty()) {
            currentMatchIndex = -1
            return
        }
        if (moveToFirst || currentMatchIndex !in matches.indices) {
            currentMatchIndex = 0
        }
        moveSelectionTo(matches[currentMatchIndex])
    }

    private fun moveSelectionTo(match: SearchMatch) {
        textFieldValue = textFieldValue.copy(selection = TextRange(match.start, match.end))
        _scrollToMatchRequests.tryEmit(match)
    }
}
