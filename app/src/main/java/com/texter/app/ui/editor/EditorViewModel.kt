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

/** [id] is null until this document has actually been written to the saved list via an explicit
 *  "Save in app" — everything else (opening, update-original, save-to-local-storage) leaves it
 *  null forever if the user never taps that action. */
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

    // Non-null only once this document exists in the saved list, so `save()` knows whether to
    // insert or update.
    private var savedEntity: SavedDocumentEntity? = null

    var documentInfo by mutableStateOf<EditorDocumentInfo?>(null)
        private set

    var textFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    // Null means "never saved in app yet" — always dirty until the first explicit save,
    // regardless of whether the freshly-opened/created content has been edited.
    private var lastSavedText: String? = null

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

    /** Emits whenever the current match changes so the screen can scroll it into view — kept
     *  separate from [textFieldValue]'s selection because scrolling needs the text layout, which
     *  only the screen has access to. */
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
                        documentInfo = EditorDocumentInfo(doc.id, doc.displayName, doc.sourceUri, doc.sourceWritable)
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

    /** The ONLY action that writes this document into the saved list — opening a file, updating
     *  the original, and saving to local storage all leave it untouched. */
    fun save() {
        val info = documentInfo ?: return
        viewModelScope.launch {
            val entity = savedEntity
            val updated = if (entity == null) {
                savedDocumentsRepository.create(
                    displayName = info.displayName,
                    content = textFieldValue.text,
                    sourceUri = info.sourceUri,
                    sourceWritable = info.sourceWritable
                )
            } else {
                savedDocumentsRepository.updateContent(entity, textFieldValue.text)
            }
            savedEntity = updated
            documentInfo = info.copy(id = updated.id)
            lastSavedText = textFieldValue.text
            _messages.tryEmit("Saved in app")
        }
    }

    /** Renames the in-memory document immediately (so it takes effect even if this session never
     *  ends up calling [save]); if it's already in the saved list, also renames the Room row. */
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
        viewModelScope.launch {
            try {
                documentIoRepository.writeText(Uri.parse(sourceUri), textFieldValue.text)
                _messages.tryEmit("Original file updated")
            } catch (e: Exception) {
                _messages.tryEmit("Couldn't update the original file")
            }
        }
    }

    fun saveToLocalStorage(targetUri: Uri) {
        viewModelScope.launch {
            try {
                documentIoRepository.writeText(targetUri, textFieldValue.text)
                _messages.tryEmit("Saved")
            } catch (e: Exception) {
                _messages.tryEmit("Couldn't save to that location")
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
